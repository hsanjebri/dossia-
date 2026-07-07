package com.example.dossia.chat;

import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ChatQueryMatcher {

    private static final Set<String> STOP_WORDS = Set.of(
            "avec", "aussi", "avoir", "bien", "comme", "cette", "dans", "depuis", "dois", "doit",
            "elle", "elles", "encore", "entre", "etre", "faire", "fait", "faut", "gens", "leur",
            "mais", "meme", "mes", "mon", "notre", "nous", "pour", "plus", "puis", "quoi",
            "sans", "sont", "tout", "tous", "toute", "tres", "une", "vers", "vous", "votre",
            "comment", "besoin", "souhaite", "veux", "voudrais", "peux", "peut", "recent",
            "recemment", "viens", "puisque", "parce", "alors", "donc", "juste", "told");

    private static final Map<String, List<String>> INTENT_SLUGS = Map.ofEntries(
            Map.entry("equivalence", List.of("equivalence-diplome")),
            Map.entry("equivalences", List.of("equivalence-diplome")),
            Map.entry("diplome", List.of("equivalence-diplome")),
            Map.entry("diplomes", List.of("equivalence-diplome")),
            Map.entry("diplomer", List.of("equivalence-diplome")),
            Map.entry("diplomee", List.of("equivalence-diplome")),
            Map.entry("graduation", List.of("equivalence-diplome")),
            Map.entry("gradue", List.of("equivalence-diplome")),
            Map.entry("graduee", List.of("equivalence-diplome")),
            Map.entry("ingenieur", List.of("equivalence-diplome")),
            Map.entry("ingenieure", List.of("equivalence-diplome")),
            Map.entry("master", List.of("equivalence-diplome")),
            Map.entry("doctorat", List.of("equivalence-diplome")),
            Map.entry("licence", List.of("equivalence-diplome")),
            Map.entry("universitaire", List.of("equivalence-diplome")),
            Map.entry("passeport", List.of("comment-obtenir-un-passeport-tunisien-facilement")),
            Map.entry("passport", List.of("comment-obtenir-un-passeport-tunisien-facilement")),
            Map.entry("carte", List.of("national-id-card-renewal", "comment-obtenir-et-renouveler-votre-carte-didentite-nationale-cin")),
            Map.entry("identite", List.of("national-id-card-renewal", "comment-obtenir-et-renouveler-votre-carte-didentite-nationale-cin")),
            Map.entry("cin", List.of("national-id-card-renewal", "comment-obtenir-et-renouveler-votre-carte-didentite-nationale-cin")),
            Map.entry("timbre", List.of("timbre-fiscal")),
            Map.entry("fiscal", List.of("timbre-fiscal")),
            Map.entry("permis", List.of("renouvellement-permis")),
            Map.entry("conduire", List.of("renouvellement-permis")));

    public List<String> extractSearchTerms(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        String normalized = normalize(query);
        String[] tokens = normalized.split("[^a-z0-9]+");
        LinkedHashSet<String> terms = new LinkedHashSet<>();

        for (String token : tokens) {
            if (token.length() < 4 || STOP_WORDS.contains(token)) {
                continue;
            }
            terms.add(token);
            if (terms.size() >= 6) {
                break;
            }
        }
        return List.copyOf(terms);
    }

    public List<String> matchIntentSlugs(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        String normalized = normalize(query);
        LinkedHashSet<String> slugs = new LinkedHashSet<>();

        for (Map.Entry<String, List<String>> entry : INTENT_SLUGS.entrySet()) {
            if (normalized.contains(entry.getKey())) {
                slugs.addAll(entry.getValue());
            }
        }
        return List.copyOf(slugs);
    }

    public String normalize(String text) {
        String withoutAccents = Normalizer.normalize(text.toLowerCase(Locale.FRENCH), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return withoutAccents.replace("œ", "oe").replace("æ", "ae");
    }
}
