package com.example.dossia.office;

import com.example.dossia.office.dto.NearbyOfficeDto;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/offices")
public class OfficeController {

    private final OfficeLocatorService officeLocatorService;

    public OfficeController(OfficeLocatorService officeLocatorService) {
        this.officeLocatorService = officeLocatorService;
    }

    @GetMapping("/nearest")
    public List<NearbyOfficeDto> nearest(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(required = false) String procedureSlug,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "5") int limit) {
        return officeLocatorService.findNearest(lat, lng, procedureSlug, q, limit);
    }
}
