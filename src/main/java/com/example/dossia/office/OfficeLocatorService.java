package com.example.dossia.office;

import com.example.dossia.office.domain.OfficeType;
import com.example.dossia.office.domain.ServicePoint;
import com.example.dossia.office.dto.NearbyOfficeDto;
import com.example.dossia.office.repository.ServicePointRepository;
import com.example.dossia.procedure.domain.OfficeLocation;
import com.example.dossia.procedure.domain.Procedure;
import com.example.dossia.procedure.domain.ProcedureCategory;
import com.example.dossia.procedure.repository.ProcedureRepository;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OfficeLocatorService {

    private final ServicePointRepository servicePointRepository;
    private final ProcedureRepository procedureRepository;

    public OfficeLocatorService(
            ServicePointRepository servicePointRepository, ProcedureRepository procedureRepository) {
        this.servicePointRepository = servicePointRepository;
        this.procedureRepository = procedureRepository;
    }

    public List<NearbyOfficeDto> findNearest(
            double latitude, double longitude, String procedureSlug, String query, int limit) {
        Set<OfficeType> types = resolveOfficeTypes(procedureSlug, query);
        String category = resolveCategory(procedureSlug);

        Map<UUID, NearbyOfficeDto> candidates = new LinkedHashMap<>();

        for (ServicePoint point : servicePointRepository.findByCategoryOrTypes(category, types)) {
            candidates.putIfAbsent(point.getId(), toDto(point, latitude, longitude, null, null));
        }

        if (procedureSlug != null && !procedureSlug.isBlank()) {
            procedureRepository.findBySlug(procedureSlug).ifPresent(procedure -> {
                for (OfficeLocation office : procedure.getOffices()) {
                    if (office.getLatitude() != null && office.getLongitude() != null) {
                        UUID key = office.getId();
                        candidates.put(
                                key,
                                toDto(office, latitude, longitude, procedure.getSlug(), procedure.getTitleFr()));
                    }
                }
            });
        }

        return candidates.values().stream()
                .sorted(Comparator.comparingDouble(NearbyOfficeDto::distanceKm))
                .limit(Math.max(1, limit))
                .toList();
    }

    public List<NearbyOfficeDto> findNearestForProcedures(
            double latitude, double longitude, List<Procedure> procedures, int limit) {
        Map<UUID, NearbyOfficeDto> candidates = new LinkedHashMap<>();

        for (Procedure procedure : procedures) {
            Set<OfficeType> types = officeTypesForCategory(procedure.getCategory());
            types.addAll(officeTypesForText(procedure.getTitleFr()));
            types.addAll(officeTypesForText(procedure.getDescriptionFr()));

            for (ServicePoint point :
                    servicePointRepository.findByCategoryOrTypes(procedure.getCategory().name(), types)) {
                candidates.putIfAbsent(
                        point.getId(),
                        toDto(point, latitude, longitude, procedure.getSlug(), procedure.getTitleFr()));
            }

            for (OfficeLocation office : procedure.getOffices()) {
                if (office.getLatitude() != null && office.getLongitude() != null) {
                    candidates.put(
                            office.getId(),
                            toDto(office, latitude, longitude, procedure.getSlug(), procedure.getTitleFr()));
                }
            }
        }

        return candidates.values().stream()
                .sorted(Comparator.comparingDouble(NearbyOfficeDto::distanceKm))
                .limit(Math.max(1, limit))
                .toList();
    }

    private Set<OfficeType> resolveOfficeTypes(String procedureSlug, String query) {
        Set<OfficeType> types = EnumSet.noneOf(OfficeType.class);
        if (procedureSlug != null && !procedureSlug.isBlank()) {
            procedureRepository.findBySlug(procedureSlug).ifPresent(p -> {
                types.addAll(officeTypesForCategory(p.getCategory()));
                types.addAll(officeTypesForText(p.getTitleFr()));
                types.addAll(officeTypesForText(p.getDescriptionFr()));
            });
        }
        types.addAll(officeTypesForText(query));
        if (types.isEmpty()) {
            types.add(OfficeType.POLICE);
            types.add(OfficeType.MUNICIPALITY);
            types.add(OfficeType.FINANCE);
        }
        return types;
    }

    private String resolveCategory(String procedureSlug) {
        if (procedureSlug == null || procedureSlug.isBlank()) {
            return "CIVIL_STATUS";
        }
        return procedureRepository
                .findBySlug(procedureSlug)
                .map(p -> p.getCategory().name())
                .orElse("CIVIL_STATUS");
    }

    private Set<OfficeType> officeTypesForCategory(ProcedureCategory category) {
        return switch (category) {
            case CIVIL_STATUS -> EnumSet.of(OfficeType.POLICE, OfficeType.MUNICIPALITY);
            case TAX -> EnumSet.of(OfficeType.FINANCE, OfficeType.POST);
            case VEHICLES -> EnumSet.of(OfficeType.ATTT);
            case BUSINESS -> EnumSet.of(OfficeType.BUSINESS);
            case SOCIAL, EDUCATION -> EnumSet.of(OfficeType.MUNICIPALITY, OfficeType.POLICE);
        };
    }

    private Set<OfficeType> officeTypesForText(String text) {
        Set<OfficeType> types = EnumSet.noneOf(OfficeType.class);
        if (text == null || text.isBlank()) {
            return types;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (containsAny(lower, "passeport", "passport", "cin", "identité", "identite", "commissariat", "police")) {
            types.add(OfficeType.POLICE);
        }
        if (containsAny(lower, "timbre", "fiscal", "finance", "impôt", "impot", "recette")) {
            types.add(OfficeType.FINANCE);
            types.add(OfficeType.POST);
        }
        if (containsAny(lower, "permis", "conduire", "attt", "véhicule", "vehicule")) {
            types.add(OfficeType.ATTT);
        }
        if (containsAny(lower, "naissance", "mariage", "municipal", "état civil", "etat civil", "résidence", "residence")) {
            types.add(OfficeType.MUNICIPALITY);
            types.add(OfficeType.POLICE);
        }
        if (containsAny(lower, "entreprise", "apii", "rne", "suarl")) {
            types.add(OfficeType.BUSINESS);
        }
        return types;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private NearbyOfficeDto toDto(
            ServicePoint point, double userLat, double userLng, String procedureSlug, String procedureTitle) {
        double distance = haversineKm(userLat, userLng, point.getLatitude(), point.getLongitude());
        return buildDto(
                point.getId(),
                point.getName(),
                point.getOfficeType().name(),
                point.getAddress(),
                point.getCity(),
                point.getGovernorate(),
                point.getHoursFr(),
                point.getLatitude(),
                point.getLongitude(),
                distance,
                procedureSlug,
                procedureTitle);
    }

    private NearbyOfficeDto toDto(
            OfficeLocation office, double userLat, double userLng, String procedureSlug, String procedureTitle) {
        double distance = haversineKm(userLat, userLng, office.getLatitude(), office.getLongitude());
        return buildDto(
                office.getId(),
                office.getName(),
                "PROCEDURE_OFFICE",
                office.getAddress(),
                office.getCity(),
                office.getGovernorate(),
                office.getHoursFr(),
                office.getLatitude(),
                office.getLongitude(),
                distance,
                procedureSlug,
                procedureTitle);
    }

    private NearbyOfficeDto buildDto(
            UUID id,
            String name,
            String officeType,
            String address,
            String city,
            String governorate,
            String hours,
            double lat,
            double lng,
            double distanceKm,
            String procedureSlug,
            String procedureTitle) {
        String mapsUrl = "https://www.openstreetmap.org/?mlat=" + lat + "&mlon=" + lng + "#map=16/" + lat + "/" + lng;
        String routeUrl = "https://www.openstreetmap.org/directions?to=" + lat + "%2C" + lng;
        return new NearbyOfficeDto(
                id,
                name,
                officeType,
                address,
                city,
                governorate,
                hours,
                lat,
                lng,
                round(distanceKm),
                procedureSlug,
                procedureTitle,
                mapsUrl,
                routeUrl);
    }

    static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1))
                        * Math.cos(Math.toRadians(lat2))
                        * Math.sin(dLon / 2)
                        * Math.sin(dLon / 2);
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
