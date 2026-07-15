package com.example.dossia.chat;

import com.example.dossia.common.Language;
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
            Map.entry("passeport", List.of(
                    "comment-obtenir-un-passeport-tunisien-facilement",
                    "demande-de-passeport-biometrique",
                    "comment-renouveler-son-passeport-tunisien-lorsquon-est-a-letranger")),
            Map.entry("passport", List.of(
                    "comment-obtenir-un-passeport-tunisien-facilement",
                    "demande-de-passeport-biometrique",
                    "comment-renouveler-son-passeport-tunisien-lorsquon-est-a-letranger")),
            Map.entry("biometrique", List.of("demande-de-passeport-biometrique")),
            Map.entry("biometric", List.of("demande-de-passeport-biometrique")),
            Map.entry("carte", List.of("national-id-card-renewal", "comment-obtenir-et-renouveler-votre-carte-didentite-nationale-cin")),
            Map.entry("identite", List.of("national-id-card-renewal", "comment-obtenir-et-renouveler-votre-carte-didentite-nationale-cin")),
            Map.entry("cin", List.of("national-id-card-renewal", "comment-obtenir-et-renouveler-votre-carte-didentite-nationale-cin")),
            Map.entry("timbre", List.of("timbre-fiscal")),
            Map.entry("fiscal", List.of("timbre-fiscal")),
            Map.entry("permis", List.of("renouvellement-permis")),
            Map.entry("conduire", List.of("renouvellement-permis")),
            Map.entry("residence", List.of("attestation-de-residence-tout-ce-que-vous-devez-savoir", "residence-certificate")),
            Map.entry("naissance", List.of("extrait-de-naissance", "acte-de-deces-tunisien-en-ligne-comment-lobtenir")),
            Map.entry("mariage", List.of("tout-savoir-sur-lobtention-de-lextrait-de-mariage-en-tunisie", "valider-son-mariage-celebre-a-letranger-tout-ce-que-vous-devez-savoir")),
            Map.entry("nationalite", List.of("tout-savoir-sur-lacquisition-de-votre-certificat-de-nationalite", "toute-la-procedure-pour-acquerir-la-nationalite-tunisienne")),
            Map.entry("livret", List.of("le-parcours-pour-obtenir-un-livret-de-famille")),
            Map.entry("famille", List.of("le-parcours-pour-obtenir-un-livret-de-famille")),
            Map.entry("etranger", List.of("formalites-dinstallation-des-residents-etrangers-en-tunisie", "comment-renouveler-son-passeport-tunisien-lorsquon-est-a-letranger")),
            Map.entry("logement", List.of("logement-social-tunisie-2025-conditions-prix-et-demarches-completes")),
            Map.entry("entreprise", List.of("creation-entreprise-suarl")),
            Map.entry("suarl", List.of("creation-entreprise-suarl")),
            Map.entry("invalidite", List.of("simplifiez-vous-la-vie-avec-la-carte-dinvalidite")),
            Map.entry("attestation", List.of("attestation-de-residence-tout-ce-que-vous-devez-savoir", "attestation-de-concordance-didentite-pour-des-papiers-sans-erreurs")));

    private static final List<String> LOCATION_CUES = List.of(
            "ou aller",
            "ou se trouve",
            "ou trouver",
            "ou deposer",
            "ou faire",
            "pres de moi",
            "pres de chez",
            "a cote",
            "bureau",
            "bureaux",
            "adresse",
            "localisation",
            "itineraire",
            "carte",
            "map",
            "gps",
            "commissariat",
            "municipalite",
            "mairie",
            "guichet",
            "agence",
            "office",
            "location",
            "nearest",
            "proche");

    private static final List<String> OFF_TOPIC_CUES = List.of(
            "meteo",
            "weather",
            "temperature",
            "football",
            "match",
            "recette",
            "cuisine",
            "film",
            "serie",
            "musique",
            "blague",
            "joke",
            "bitcoin",
            "crypto",
            "horoscope",
            "amour",
            "dating",
            "jeu video",
            "fortnite",
            "instagram",
            "tiktok",
            "traduis",
            "translate",
            "code python",
            "homework",
            "devoir de maths");

    private static final List<String> CRISIS_CUES = List.of(
            "suicide",
            "kill myself",
            "kill me",
            "me tuer",
            "me suicider",
            "veux mourir",
            "envie de mourir",
            "end my life",
            "self harm",
            "me faire du mal");

    private static final List<String> ADMIN_CUES = List.of(
            "demarche",
            "procedure",
            "document",
            "papier",
            "formulaire",
            "ministere",
            "administration",
            "tunisie",
            "tunisien",
            "tunisienne",
            "guichet",
            "timbre",
            "extrait",
            "acte",
            "dossier",
            "renouveler",
            "obtenir",
            "demande");

    /** Short replies that continue a prior assistant turn (option picks, yes/no, location). */
    public boolean isFollowUp(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        String trimmed = query.strip();
        String normalized = normalize(trimmed)
                .replaceAll("[!?.\"'«»]+", " ")
                .replaceAll("\\s+", " ")
                .strip();
        if (normalized.matches("^\\d{1,2}$")) {
            return true;
        }
        if (normalized.matches("^(1|2|3|4|5)\\s*[).:-]?.*$") && trimmed.length() < 80) {
            return true;
        }
        if (normalized.matches(
                "^(oui|non|ok|okay|daccord|d accord|en tunisie|a l etranger|"
                        + "tunisie|etranger|le premier|le second|la premiere|la deuxieme|"
                        + "option 1|option 2|premier|deuxieme|celui la|celle la|"
                        + "how|how to|the first|the second|both|this one|that one|"
                        + "go on|continue|next|please|pls|bro|yes|yeah|yep|nope)$")) {
            return true;
        }
        // "ok i wanna choose", "give me the papers", typos included
        if (normalized.matches(".*(choose|choisir|option|papers|pappers|papiers|documents|docs|"
                + "pieces|justificatifs|what do i need|give me|liste).*" )
                && trimmed.length() < 120) {
            return true;
        }
        return false;
    }

    /**
     * When a conversation is already open, short / messy messages should continue the thread
     * instead of being treated as a brand-new gibberish question.
     */
    public boolean isThreadContinuation(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        if (isFollowUp(query) || isDocumentAsk(query)) {
            return true;
        }
        String trimmed = query.strip();
        // Quoted procedure title or paste of a title
        if ((trimmed.startsWith("\"") || trimmed.startsWith("«") || trimmed.contains("Comment "))
                && trimmed.length() < 160) {
            return true;
        }
        // Casual / short English-French mix
        return trimmed.length() <= 80 && (isLowSignalQuery(query) || extractSearchTerms(query).isEmpty());
    }

    public boolean isDocumentAsk(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        String normalized = normalize(query);
        return List.of(
                        "paper",
                        "papers",
                        "pappers",
                        "papier",
                        "papiers",
                        "document",
                        "documents",
                        "docs",
                        "pieces",
                        "piece",
                        "justificatif",
                        "justificatifs",
                        "required",
                        "requis",
                        "what do i need",
                        "quoi apporter",
                        "liste")
                .stream()
                .anyMatch(normalized::contains);
    }

    public boolean isCrisisQuery(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        String normalized = normalize(query);
        return CRISIS_CUES.stream().anyMatch(normalized::contains);
    }

    public boolean isOffTopicQuery(String query) {
        if (query == null || query.isBlank() || isGreeting(query) || isCrisisQuery(query)) {
            return false;
        }
        if (isLocationIntent(query) || !matchIntentSlugs(query).isEmpty()) {
            return false;
        }
        String normalized = normalize(query);
        if (ADMIN_CUES.stream().anyMatch(normalized::contains)) {
            return false;
        }
        return OFF_TOPIC_CUES.stream().anyMatch(normalized::contains);
    }

    public boolean isConversational(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        if (isGreeting(query)) {
            return true;
        }
        String normalized = normalize(query.strip())
                .replaceAll("[!?.]+", " ")
                .replaceAll("\\s+", " ")
                .strip();
        return normalized.matches(
                "^(ca va|comment ca va|comment vas tu|qui es tu|tu es qui|ton nom|c est quoi ton nom|"
                        + "how are you|who are you|what can you do|que peux tu faire|"
                        + "tu peux m aider\\??|peux tu m aider\\??)$");
    }

    public boolean isLocationIntent(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        String normalized = normalize(query);
        return LOCATION_CUES.stream().anyMatch(normalized::contains);
    }

    /** True for greetings / thanks / short social messages (not procedure questions). */
    public boolean isGreeting(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        String normalized = normalize(query.strip())
                .replaceAll("[!?.]+", " ")
                .replaceAll("\\s+", " ")
                .strip();
        if (normalized.matches("^(salut|hello|hi|hey|bonjour|bonsoir|hola|yo|salaam|salam)( again| encore| a toi| a vous)?$")) {
            return true;
        }
        if (normalized.matches("^(merci|thanks|thank you|ok|daccord|d accord|bye|au revoir)$")) {
            return true;
        }
        // "hiii", "heyyy", "hellooo" style
        return normalized.matches("^(h+i+|he+y+|hello+|salut+|bonjour+)( again| encore)?$");
    }

    /** True for gibberish or messages too short to be a real procedure question (not greetings). */
    public boolean isLowSignalQuery(String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        if (isGreeting(query)) {
            return false;
        }
        String trimmed = query.strip();
        if (trimmed.length() < 3) {
            return true;
        }
        String normalized = normalize(trimmed);
        // Mostly digits / nonsense tokens with no intent keywords ("hello4", "hoiii4", "cv")
        if (normalized.matches("^[a-z]{1,6}\\d+$") || normalized.matches("^\\d+$")) {
            return true;
        }
        if (normalized.matches("^(ok|oui|non|cv|wtf|lol|test)+\\d*$")) {
            return true;
        }
        if (!matchIntentSlugs(trimmed).isEmpty()) {
            return false;
        }
        return extractSearchTerms(trimmed).isEmpty() && trimmed.length() < 10;
    }

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

    /**
     * Detect reply language from the user message (and prior user turns for short follow-ups).
     * French / English / Arabic (incl. Tunisian Arabic script).
     */
    public Language detectReplyLanguage(String message, List<String> priorUserMessages) {
        String sample = message == null ? "" : message.strip();
        if (isFollowUp(sample) || sample.length() < 4) {
            for (int i = priorUserMessages.size() - 1; i >= 0; i--) {
                String prior = priorUserMessages.get(i);
                if (prior != null && !prior.isBlank() && !isFollowUp(prior)) {
                    sample = prior;
                    break;
                }
            }
        }
        return detectReplyLanguage(sample);
    }

    public Language detectReplyLanguage(String text) {
        if (text == null || text.isBlank()) {
            return Language.FR;
        }
        if (text.codePoints().anyMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.ARABIC)) {
            return Language.AR;
        }

        String trimmedLower = text.strip().toLowerCase(Locale.ENGLISH);
        if (trimmedLower.matches("^(h+i+|he+y+|hello+|yo+)[!?.]*$")
                || trimmedLower.matches("^(good\\s+)?(morning|evening|afternoon)[!?.]*$")) {
            return Language.EN;
        }
        if (trimmedLower.matches("^(sal+am|salaam|ahlan|marhaba)[!?.]*$")) {
            return Language.AR;
        }

        String normalized = normalize(text);
        int fr = countHits(
                normalized,
                "je",
                "tu",
                "vous",
                "bonjour",
                "bonsoir",
                "merci",
                "comment",
                "renouveler",
                "passeport",
                "carte",
                "identite",
                "diplome",
                "demarche",
                "svp",
                "ou",
                "pour",
                "faire",
                "obtenir",
                "besoin");
        int en = countHits(
                normalized,
                "i",
                "my",
                "me",
                "renew",
                "passport",
                "how",
                "what",
                "where",
                "need",
                "want",
                "please",
                "hello",
                "hi",
                "the",
                "can",
                "could",
                "would",
                "identity",
                "card",
                "diploma",
                "wanna",
                "iwanna",
                "pls");

        if (en > fr && en > 0) {
            return Language.EN;
        }
        if (fr > 0) {
            return Language.FR;
        }
        // Informal Latin without French cues → English (e.g. "iwna renew my passport")
        if (normalized.matches(".*[a-z].*") && en >= fr) {
            return Language.EN;
        }
        return Language.FR;
    }

    private int countHits(String normalized, String... words) {
        int hits = 0;
        for (String word : words) {
            if (normalized.matches(".*\\b" + word + "\\b.*")) {
                hits++;
            }
        }
        return hits;
    }
}
