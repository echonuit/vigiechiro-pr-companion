package fr.univ_amu.iut.passage.model;

import fr.univ_amu.iut.commun.model.JsonSimple;
import fr.univ_amu.iut.commun.model.MethodeSelection;
import fr.univ_amu.iut.commun.model.VerdictFichier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Ce que le paquet dit de ce qu'il emporte (#4705, ADR 4517 et ADR 4627).
///
/// Les **verdicts déjà posés** voyagent : sans eux le relecteur jugerait à l'aveugle, et l'avis qui
/// revient n'aurait rien à confronter.
///
/// **Trois tableaux parallèles plutôt que des objets imbriqués.** [JsonSimple] écrit des objets plats
/// et des tableaux de chaînes ; imbriquer demanderait un sérialiseur que ce dépôt n'a délibérément
/// pas. Le désaccord que des tableaux parallèles rendent possible est **refusé** à la relecture, pas
/// deviné.
///
/// @param carre numéro du carré du site
/// @param point code du point d'écoute
/// @param annee année de la campagne
/// @param nuit numéro de la nuit dans la campagne
/// @param methode comment la sélection a été tirée, pour que le relecteur le sache
/// @param pseudoJugeur qui a posé les verdicts que ce manifeste porte, `null` si personne
/// @param sequences ce que le paquet emporte, dans l'ordre de la sélection
public record ManifestePaquet(
        String carre,
        String point,
        int annee,
        int nuit,
        MethodeSelection methode,
        String pseudoJugeur,
        List<SequenceEmportee> sequences) {

    /// Un manifeste dont personne n'a encore jugé les séquences.
    ///
    /// @param carre numéro du carré du site
    /// @param point code du point d'écoute
    /// @param annee année de la campagne
    /// @param nuit numéro de la nuit dans la campagne
    /// @param methode comment la sélection a été tirée
    /// @param sequences ce que le paquet emporte
    public ManifestePaquet(
            String carre,
            String point,
            int annee,
            int nuit,
            MethodeSelection methode,
            List<SequenceEmportee> sequences) {
        this(carre, point, annee, nuit, methode, null, sequences);
    }

    /// Une séquence emportée, avec le verdict que l'expéditeur avait déjà posé.
    ///
    /// @param nomFichier nom du fichier dans l'archive, sans son dossier
    /// @param position rang dans la sélection
    /// @param verdict verdict de l'expéditeur, [VerdictFichier#NON_JUGE] s'il n'a pas encore jugé
    public record SequenceEmportee(String nomFichier, int position, VerdictFichier verdict) {

        public SequenceEmportee {
            Objects.requireNonNull(nomFichier, "nomFichier");
            verdict = verdict == null ? VerdictFichier.NON_JUGE : verdict;
        }
    }

    /// Les clés du manifeste, nommées une fois : celle qu'on écrit **doit** être celle qu'on relit.
    private static final String CARRE = "carre";

    private static final String POINT = "point";

    private static final String ANNEE = "annee";

    private static final String NUIT = "nuit";

    private static final String METHODE = "methode";

    private static final String JUGEUR = "jugeur";

    private static final String SEQUENCES = "sequences";

    private static final String POSITIONS = "positions";

    private static final String VERDICTS = "verdicts";

    private static final String ILLISIBLE = "manifeste illisible : ";

    private static final String LE_TABLEAU = "le tableau « ";

    public ManifestePaquet {
        Objects.requireNonNull(carre, CARRE);
        Objects.requireNonNull(point, POINT);
        Objects.requireNonNull(methode, METHODE);
        sequences = List.copyOf(Objects.requireNonNull(sequences, SEQUENCES));
    }

    /// Le manifeste en JSON, tel qu'il sera écrit à la racine de l'archive.
    public String texte() {
        Map<String, String> entetes = new LinkedHashMap<>();
        entetes.put(CARRE, carre);
        entetes.put(POINT, point);
        entetes.put(ANNEE, String.valueOf(annee));
        entetes.put(NUIT, String.valueOf(nuit));
        entetes.put(METHODE, methode.name());
        entetes.put(JUGEUR, pseudoJugeur == null ? "" : pseudoJugeur);
        String plat = JsonSimple.objet(entetes);

        return plat.substring(0, plat.length() - 1)
                + entree(SEQUENCES)
                + JsonSimple.tableau(
                        sequences.stream().map(SequenceEmportee::nomFichier).toList())
                + entree(POSITIONS)
                + JsonSimple.tableau(sequences.stream()
                        .map(s -> String.valueOf(s.position()))
                        .toList())
                + entree(VERDICTS)
                + JsonSimple.tableau(
                        sequences.stream().map(s -> s.verdict().name()).toList())
                + "}";
    }

    /// Le fragment `,"clé":` qui précède la valeur d'un tableau.
    private static String entree(String cle) {
        return ",\"" + cle + "\":";
    }

    /// Relit un manifeste écrit par [#texte()].
    ///
    /// @param texte le contenu du manifeste
    /// @return le manifeste relu
    /// @throws IllegalArgumentException si une clé manque ou si les trois tableaux se désaccordent :
    ///     rendre un manifeste vide ferait passer un paquet corrompu pour un paquet sans séquence
    public static ManifestePaquet depuis(String texte) {
        Objects.requireNonNull(texte, "texte");
        List<String> noms = tableau(texte, SEQUENCES);
        List<String> positions = tableau(texte, POSITIONS);
        List<String> verdicts = tableau(texte, VERDICTS);
        if (noms.size() != positions.size() || noms.size() != verdicts.size()) {
            throw new IllegalArgumentException("manifeste désaccordé : " + noms.size() + " séquence(s), "
                    + positions.size() + " position(s), " + verdicts.size() + " verdict(s)");
        }
        List<SequenceEmportee> emportees = new ArrayList<>();
        for (int i = 0; i < noms.size(); i++) {
            emportees.add(new SequenceEmportee(
                    noms.get(i), entier(positions.get(i), "position"), VerdictFichier.valueOf(verdicts.get(i))));
        }
        String jugeur = scalaire(texte, JUGEUR);
        return new ManifestePaquet(
                scalaire(texte, CARRE),
                scalaire(texte, POINT),
                entier(scalaire(texte, ANNEE), ANNEE),
                entier(scalaire(texte, NUIT), NUIT),
                MethodeSelection.valueOf(scalaire(texte, METHODE)),
                jugeur.isEmpty() ? null : jugeur,
                emportees);
    }

    private static final Pattern CHAINE = Pattern.compile("\"((?:\\\\.|[^\"\\\\])*)\"");

    private static String scalaire(String texte, String cle) {
        Matcher trouve = Pattern.compile("\"" + cle + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
                .matcher(texte);
        if (!trouve.find()) {
            throw new IllegalArgumentException(ILLISIBLE + "la clé « " + cle + " » manque");
        }
        return desechappe(trouve.group(1));
    }

    /// Les valeurs d'un tableau de chaînes, en **balayant** plutôt qu'en cherchant le `]` le plus
    /// proche : `]` n'est pas un caractère échappé en JSON, donc un nom de fichier qui en porte un
    /// clôturerait le tableau trop tôt. Le balayage ne s'arrête qu'à un `]` **hors chaîne**.
    private static List<String> tableau(String texte, String cle) {
        Matcher ouverture = Pattern.compile("\"" + cle + "\"\\s*:\\s*\\[").matcher(texte);
        if (!ouverture.find()) {
            throw new IllegalArgumentException(ILLISIBLE + LE_TABLEAU + cle + " » manque");
        }
        List<String> valeurs = new ArrayList<>();
        int i = ouverture.end();
        while (i < texte.length() && texte.charAt(i) != ']') {
            if (texte.charAt(i) != '"') {
                i++;
                continue;
            }
            Matcher chaine = CHAINE.matcher(texte);
            if (!chaine.find(i) || chaine.start() != i) {
                throw new IllegalArgumentException(ILLISIBLE + LE_TABLEAU + cle + " » porte une chaîne inachevée");
            }
            valeurs.add(desechappe(chaine.group(1)));
            i = chaine.end();
        }
        if (i >= texte.length()) {
            throw new IllegalArgumentException(ILLISIBLE + LE_TABLEAU + cle + " » n'est pas fermé");
        }
        return valeurs;
    }

    private static int entier(String brut, String quoi) {
        try {
            return Integer.parseInt(brut);
        } catch (NumberFormatException pasUnNombre) {
            throw new IllegalArgumentException(ILLISIBLE + "« " + quoi + " » vaut « " + brut + " »");
        }
    }

    /// L'inverse exact de [JsonSimple#echapper] : les cinq séquences qu'il produit, et `\\uXXXX`.
    ///
    /// **Deux mutants survivent ici, et c'est assumé.** Le `i + 1 >= brut.length()` protège d'une
    /// barre oblique isolée en fin de chaîne. Aucun test ne le tue parce qu'aucune entrée ne peut
    /// l'atteindre : cette méthode ne reçoit que ce que `CHAINE` a déjà reconnu, or ce motif exige
    /// que toute barre oblique en escorte une autre. La garde reste pour un appelant futur qui
    /// lui passerait du texte brut, et ce serait alors un débordement d'indice plutôt qu'un refus.
    private static String desechappe(String brut) {
        StringBuilder sortie = new StringBuilder(brut.length());
        for (int i = 0; i < brut.length(); i++) {
            char c = brut.charAt(i);
            if (c != '\\' || i + 1 >= brut.length()) {
                sortie.append(c);
                continue;
            }
            char suivant = brut.charAt(++i);
            switch (suivant) {
                case 'n' -> sortie.append('\n');
                case 'r' -> sortie.append('\r');
                case 't' -> sortie.append('\t');
                case 'u' -> {
                    sortie.append((char) Integer.parseInt(brut.substring(i + 1, i + 5), 16));
                    i += 4;
                }
                default -> sortie.append(suivant);
            }
        }
        return sortie.toString();
    }
}
