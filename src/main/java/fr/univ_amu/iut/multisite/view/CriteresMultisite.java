package fr.univ_amu.iut.multisite.view;

import fr.univ_amu.iut.commun.model.NormalisationTexte;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.model.VueSauvegardee;
import fr.univ_amu.iut.commun.view.CritereFiltre;
import fr.univ_amu.iut.commun.view.DescripteurCritere;
import fr.univ_amu.iut.commun.view.DescripteurFiltre;
import fr.univ_amu.iut.commun.view.DescripteurFiltreJson;
import fr.univ_amu.iut.multisite.model.FiltresMultisite;
import fr.univ_amu.iut.multisite.model.LignePassage;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;

/// Catalogue des **critères de filtrage** de la vue multi-sites (patron « à la Notion », #537 étape 6b).
/// Chaque critère est une puce ajoutable : **Carré** (n° de carré, champ texte), **Statut** de workflow,
/// **Verdict** de vérification et **Année**. La **recherche texte** permanente ([#rechercheTexte()]) couvre
/// carré, point et date.
///
/// Pendant, côté multisite, du `CriteresAudio` / `CriteresAnalyse`. Les prédicats **réutilisent** la
/// sémantique de [FiltresMultisite#accepte(LignePassage)] : aucune logique de filtrage dupliquée. Aucune
/// présélection : ajouter une puce n'écarte rien tant qu'une valeur n'est pas saisie.
final class CriteresMultisite {

    /// Clé **stable** du critère Statut, partagée par le critère et les vues par défaut (évite un littéral
    /// dupliqué).
    private static final String STATUT = "statut";

    private CriteresMultisite() {}

    /// Vues **par défaut** (lecture seule) du tableau des passages, rendues comme onglets avant les vues de
    /// l'utilisateur (#623), sur le modèle de `CriteresAudio` :
    /// - **« Tout »** (aucun filtre) : active au chargement, n'écarte rien ;
    /// - **« Déposés »** (statut Déposé : nuits déjà envoyées) ;
    /// - **« À vérifier »** (verdict À vérifier : passages à contrôler) ;
    /// - **« Vérifiés »** (statut Vérifié).
    ///
    /// Chaque descripteur est sérialisé exactement comme [GestionnaireFiltres#decrire()] le produirait, pour
    /// que rejouer la vue laisse un état « non modifié ».
    static List<VueSauvegardee> vuesParDefaut() {
        return List.of(
                vueParDefaut("Tout"),
                vueParDefaut("Déposés", new DescripteurCritere(STATUT, List.of(StatutWorkflow.DEPOSE.name()))),
                vueParDefaut("À vérifier", new DescripteurCritere("verdict", List.of(Verdict.A_VERIFIER.name()))),
                vueParDefaut("Vérifiés", new DescripteurCritere(STATUT, List.of(StatutWorkflow.VERIFIE.name()))));
    }

    /// Une vue par défaut : `id` **nul** (jamais persistée → lecture seule) et descripteur des critères donnés
    /// (aucun critère = vue « Tout », sans filtre).
    private static VueSauvegardee vueParDefaut(String nom, DescripteurCritere... criteres) {
        String descripteur = DescripteurFiltreJson.serialiser(new DescripteurFiltre("", List.of(criteres)));
        return new VueSauvegardee(null, "multisite", nom, descripteur);
    }

    /// Critère **Carré** : champ texte du n° de carré (ex. `640380`). Éditable au clavier **et** posé par la
    /// carte (clic d'un carré, via [fr.univ_amu.iut.commun.view.GestionnaireFiltres#poser(String, List)]).
    static CritereFiltre<LignePassage> carre() {
        return new CritereFiltre<LignePassage>() {
            @Override
            public String nom() {
                return "carre";
            }

            @Override
            public String libelle() {
                return "Carré";
            }

            @Override
            public Node editeur(Consumer<Predicate<LignePassage>> applique) {
                TextField champ = new TextField();
                champ.setPromptText("N° carré");
                champ.textProperty().addListener((obs, avant, texte) -> applique.accept(predicatCarre(texte)));
                applique.accept(tout()); // pas de valeur → aucun filtre
                return champ;
            }

            @Override
            public List<String> valeurCourante(Node editeur) {
                String carre = texteOuNull(((TextField) editeur).getText());
                return carre == null ? List.of() : List.of(carre);
            }

            @Override
            public void restaurerValeurs(Node editeur, List<String> valeurs) {
                ((TextField) editeur).setText(valeurs.isEmpty() ? "" : valeurs.get(0));
            }
        };
    }

    /// Critère **Statut de workflow** : liste déroulante, sans présélection.
    static CritereFiltre<LignePassage> statut() {
        return new CritereFiltre<LignePassage>() {
            @Override
            public String nom() {
                return STATUT;
            }

            @Override
            public String libelle() {
                return "Statut";
            }

            @Override
            public Node editeur(Consumer<Predicate<LignePassage>> applique) {
                ComboBox<StatutWorkflow> choix = new ComboBox<>();
                choix.getItems().setAll(StatutWorkflow.values());
                choix.setPromptText("Choisir un statut");
                choix.setConverter(convertisseur(s -> s == null ? "" : s.libelle()));
                choix.valueProperty()
                        .addListener((obs, avant, statut) ->
                                applique.accept(statut == null ? tout() : FiltresMultisite.parStatut(statut)::accepte));
                applique.accept(tout());
                return choix;
            }

            @Override
            public List<String> valeurCourante(Node editeur) {
                Object valeur = ((ComboBox<?>) editeur).getValue();
                return valeur == null ? List.of() : List.of(((StatutWorkflow) valeur).name());
            }

            @Override
            public void restaurerValeurs(Node editeur, List<String> valeurs) {
                if (!valeurs.isEmpty()) {
                    selectionnerParValeur(editeur, StatutWorkflow.valueOf(valeurs.get(0)));
                }
            }
        };
    }

    /// Critère **Verdict de vérification** : liste déroulante, sans présélection.
    static CritereFiltre<LignePassage> verdict() {
        return new CritereFiltre<LignePassage>() {
            @Override
            public String nom() {
                return "verdict";
            }

            @Override
            public String libelle() {
                return "Verdict";
            }

            @Override
            public Node editeur(Consumer<Predicate<LignePassage>> applique) {
                ComboBox<Verdict> choix = new ComboBox<>();
                choix.getItems().setAll(Verdict.values());
                choix.setPromptText("Choisir un verdict");
                choix.setConverter(convertisseur(v -> v == null ? "" : v.libelle()));
                choix.valueProperty()
                        .addListener((obs, avant, verdict) -> applique.accept(
                                verdict == null ? tout() : FiltresMultisite.parVerdict(verdict)::accepte));
                applique.accept(tout());
                return choix;
            }

            @Override
            public List<String> valeurCourante(Node editeur) {
                Object valeur = ((ComboBox<?>) editeur).getValue();
                return valeur == null ? List.of() : List.of(((Verdict) valeur).name());
            }

            @Override
            public void restaurerValeurs(Node editeur, List<String> valeurs) {
                if (!valeurs.isEmpty()) {
                    selectionnerParValeur(editeur, Verdict.valueOf(valeurs.get(0)));
                }
            }
        };
    }

    /// Critère **Année** : champ texte numérique (une saisie non numérique ne filtre pas).
    static CritereFiltre<LignePassage> annee() {
        return new CritereFiltre<LignePassage>() {
            @Override
            public String nom() {
                return "annee";
            }

            @Override
            public String libelle() {
                return "Année";
            }

            @Override
            public Node editeur(Consumer<Predicate<LignePassage>> applique) {
                TextField champ = new TextField();
                champ.setPromptText("Année");
                champ.textProperty().addListener((obs, avant, texte) -> applique.accept(predicatAnnee(texte)));
                applique.accept(tout());
                return champ;
            }

            @Override
            public List<String> valeurCourante(Node editeur) {
                Integer annee = anneeOuNull(((TextField) editeur).getText());
                return annee == null ? List.of() : List.of(annee.toString());
            }

            @Override
            public void restaurerValeurs(Node editeur, List<String> valeurs) {
                ((TextField) editeur).setText(valeurs.isEmpty() ? "" : valeurs.get(0));
            }
        };
    }

    /// **Recherche texte** de la barre : vrai si le n° de carré, le code du point ou la date d'une ligne
    /// contient l'aiguille (insensible casse/accents). Fournie au `GestionnaireFiltres` (champ permanent).
    static BiPredicate<LignePassage, String> rechercheTexte() {
        return CriteresMultisite::correspond;
    }

    private static boolean correspond(LignePassage ligne, String texte) {
        String aiguille = NormalisationTexte.normaliser(texte);
        return contient(ligne.numeroCarre(), aiguille)
                || contient(ligne.codePoint(), aiguille)
                || contient(ligne.dateEnregistrement(), aiguille);
    }

    private static boolean contient(String champ, String aiguille) {
        return champ != null && NormalisationTexte.normaliser(champ).contains(aiguille);
    }

    private static Predicate<LignePassage> predicatCarre(String texte) {
        String carre = texteOuNull(texte);
        return carre == null ? tout() : FiltresMultisite.parSite(carre)::accepte;
    }

    private static Predicate<LignePassage> predicatAnnee(String texte) {
        Integer annee = anneeOuNull(texte);
        return annee == null ? tout() : FiltresMultisite.parAnnee(annee)::accepte;
    }

    /// Prédicat neutre (aucun filtre) : la puce est présente mais ne restreint rien tant qu'aucune valeur
    /// n'est saisie (contrat [CritereFiltre#editeur] : appliquer un prédicat **non nul**).
    private static Predicate<LignePassage> tout() {
        return ligne -> true;
    }

    private static String texteOuNull(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.trim();
    }

    /// Année saisie, ou `null` si vide/non numérique.
    private static Integer anneeOuNull(String texte) {
        String annee = texteOuNull(texte);
        if (annee == null) {
            return null;
        }
        try {
            return Integer.valueOf(annee);
        } catch (NumberFormatException saisieNonNumerique) {
            return null;
        }
    }

    /// Sélectionne dans une liste déroulante l'élément **égal** à `valeur` (ou vide la sélection s'il est
    /// absent : `indexOf` → -1), pour restaurer une valeur mémorisée sans cast générique non vérifié.
    private static void selectionnerParValeur(Node editeur, Object valeur) {
        ComboBox<?> choix = (ComboBox<?>) editeur;
        choix.getSelectionModel().select(choix.getItems().indexOf(valeur));
    }

    private static <T> StringConverter<T> convertisseur(Function<T, String> versTexte) {
        return new StringConverter<>() {
            @Override
            public String toString(T valeur) {
                return versTexte.apply(valeur);
            }

            @Override
            public T fromString(String libelle) {
                return null; // liste non éditable
            }
        };
    }
}
