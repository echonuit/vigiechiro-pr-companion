package fr.univ_amu.iut.commun.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// Le **référentiel d'activité** ACTICHIRO / Vigie-Chiro (#2351) : pour une espèce, une saison et un
/// contexte, les quantiles qui disent où se situe un nombre de contacts.
///
/// ## La source, qui doit voyager avec la donnée
///
/// > Bas Y., Kerbiriou C., Roemer C. & Julien J.-F. (2020) — *Bat reference scale of activity levels*
/// > (v. 2020-04-10), Team-Chiro / CESCO — Muséum national d'Histoire naturelle.
///
/// Données ouvertes, **libres d'usage avec citation obligatoire**. C'est pourquoi [#CITATION] est
/// exposée : elle se recopie à l'écran et dans chaque export. Un référentiel scientifique qui voyage
/// sans sa source est une donnée orpheline, et l'utilisateur n'a alors aucun moyen de contester ce
/// qu'on lui affirme.
///
/// ## La règle de repli
///
/// On cherche du **plus précis au plus général** — milieu, puis région, puis national — mais on retient
/// la **première déclinaison fiable**, pas la plus fine. Descendre vers un seuil peu fiable parce qu'il
/// est plus spécifique produit une classe plus **fausse**, pas plus juste.
///
/// Quand aucune déclinaison fiable n'existe, on rend la plus précise des non fiables plutôt que rien :
/// l'appelant l'affichera **marquée indicatif** ([SeuilsActivite#indicatif()]). Ne rien dire ferait
/// croire à une absence de données là où il n'y a qu'une incertitude assumée.
///
/// ## Ce que ce référentiel ne couvre pas
///
/// Les taxons hors chiroptères (orthoptères, oiseaux, bruit) n'ont pas de seuils : la lecture rend
/// alors **vide**, et l'écran doit écrire « non couvert par le référentiel » plutôt que de laisser une
/// cellule blanche, qui se lirait comme une donnée manquante.
///
/// Sans dépendance JavaFX : cette classe se teste seule.
public final class ReferentielActivite {

    /// Citation **obligatoire**, à recopier partout où une classe d'activité s'affiche ou s'exporte.
    public static final String CITATION =
            "Bas Y., Kerbiriou C., Roemer C. & Julien J.-F. (2020) - Bat reference scale of activity "
                    + "levels (v. 2020-04-10), Team-Chiro / CESCO - Museum national d'Histoire naturelle";

    /// Ce que la classe d'activité **ne dit pas**. Accompagne la citation, écran comme export.
    public static final String AVERTISSEMENT =
            "Une classe d'activite n'est pas un niveau d'enjeu de conservation, et les classes ne se "
                    + "comparent pas d'une espece a l'autre : la detectabilite varie trop d'un taxon au suivant.";

    private static final String RESSOURCE = "referentiel-activite.csv";
    private static final String NATIONAL = "national";
    private static final String TOUTES_SAISONS = "toutes";

    /// (code taxon, déclinaison, saison) → seuils.
    private final Map<Cle, SeuilsActivite> seuils;

    private ReferentielActivite(Map<Cle, SeuilsActivite> seuils) {
        this.seuils = Map.copyOf(seuils);
    }

    /// Charge la ressource embarquée. À faire **une fois** : le référentiel ne change pas en cours de
    /// session, et le relire par ligne affichée laisserait croire le contraire.
    public static ReferentielActivite embarque() {
        try (InputStream flux = ReferentielActivite.class.getResourceAsStream(RESSOURCE)) {
            if (flux == null) {
                throw new IllegalStateException("Ressource du referentiel d'activite introuvable : " + RESSOURCE);
            }
            return lire(new InputStreamReader(flux, StandardCharsets.UTF_8));
        } catch (IOException erreur) {
            throw new UncheckedIOException("Lecture du referentiel d'activite impossible", erreur);
        }
    }

    /// Lit un référentiel au format de la ressource : lignes de commentaire `#`, ligne d'en-tête, puis
    /// `code;referentiel;saison;q25;q75;q98;nbocc;confiance`.
    static ReferentielActivite lire(java.io.Reader source) throws IOException {
        Map<Cle, SeuilsActivite> table = new HashMap<>();
        try (BufferedReader lecteur = new BufferedReader(source)) {
            String ligne;
            while ((ligne = lecteur.readLine()) != null) {
                if (ligne.isBlank() || ligne.startsWith("#") || ligne.startsWith("code;")) {
                    continue;
                }
                ajouter(table, ligne);
            }
        }
        return new ReferentielActivite(table);
    }

    private static void ajouter(Map<Cle, SeuilsActivite> table, String ligne) {
        String[] champs = ligne.split(";", -1);
        if (champs.length < 8) {
            return;
        }
        Optional<ConfianceReferentiel> confiance = ConfianceReferentiel.depuis(champs[7]);
        if (confiance.isEmpty()) {
            // Une confiance illisible n'est pas une confiance moyenne : la ligne est écartée plutôt que
            // rangée d'office du côté fiable.
            return;
        }
        try {
            table.put(
                    new Cle(champs[0], champs[1], champs[2]),
                    new SeuilsActivite(
                            Integer.parseInt(champs[3].trim()),
                            Integer.parseInt(champs[4].trim()),
                            Integer.parseInt(champs[5].trim()),
                            Integer.parseInt(champs[6].trim()),
                            confiance.get(),
                            champs[1],
                            champs[2]));
        } catch (NumberFormatException quantileIllisible) {
            // Même raison : une ligne dont un quantile ne se lit pas ne doit pas produire de seuil.
        }
    }

    /// Les seuils à retenir pour ce taxon, dans ce contexte — ou **vide** si le taxon n'est pas couvert.
    ///
    /// L'ordre d'essai va du plus précis au plus général, et **s'arrête à la première déclinaison
    /// fiable**. Faute de fiable, la plus précise des non fiables est rendue, à charge pour l'appelant
    /// de la marquer indicative.
    ///
    /// @param codeTaxon code Tadarida, dans la casse du référentiel (`Pipkuh`)
    /// @param contexte saison, région et milieu tels qu'on les connaît — chacun facultatif
    public Optional<SeuilsActivite> pour(String codeTaxon, ContexteActivite contexte) {
        if (codeTaxon == null) {
            return Optional.empty();
        }
        List<SeuilsActivite> candidats = candidats(codeTaxon, contexte);
        return candidats.stream()
                .filter(SeuilsActivite::fiable)
                .findFirst()
                .or(() -> candidats.stream().findFirst());
    }

    /// Les déclinaisons applicables, **dans l'ordre de préférence** : milieu, région, national ; et pour
    /// chacune, la saison précise avant `toutes`.
    private List<SeuilsActivite> candidats(String codeTaxon, ContexteActivite contexte) {
        List<SeuilsActivite> trouves = new ArrayList<>();
        for (String declinaison : contexte.declinaisonsParPrecision()) {
            for (String saison : contexte.saisonsParPrecision()) {
                SeuilsActivite candidat = seuils.get(new Cle(codeTaxon, declinaison, saison));
                if (candidat != null) {
                    trouves.add(candidat);
                }
            }
        }
        return trouves;
    }

    /// Ce taxon figure-t-il au référentiel, quelle que soit la déclinaison ? Sert à distinguer « non
    /// couvert » (un orthoptère) de « couvert mais sans seuil pour ce contexte ».
    public boolean couvre(String codeTaxon) {
        return codeTaxon != null
                && (seuils.containsKey(new Cle(codeTaxon, NATIONAL, TOUTES_SAISONS))
                        || seuils.keySet().stream()
                                .anyMatch(cle -> cle.codeTaxon().equals(codeTaxon)));
    }

    /// Nombre de jeux de seuils chargés : sert aux gardes de justesse de la ressource.
    public int taille() {
        return seuils.size();
    }

    /// Les déclinaisons que la ressource porte réellement, pour confronter le tableau des régions à ce
    /// qui existe vraiment.
    public java.util.Set<String> declinaisons() {
        return seuils.keySet().stream().map(Cle::declinaison).collect(java.util.stream.Collectors.toSet());
    }

    private record Cle(String codeTaxon, String declinaison, String saison) {}
}
