package fr.univ_amu.iut.analyse.view;

import fr.univ_amu.iut.commun.model.NormalisationTexte;
import fr.univ_amu.iut.commun.model.VueSauvegardee;
import fr.univ_amu.iut.commun.view.CritereFiltre;
import fr.univ_amu.iut.commun.view.DescripteurCritere;
import fr.univ_amu.iut.commun.view.DescripteurFiltre;
import fr.univ_amu.iut.commun.view.DescripteurFiltreJson;
import fr.univ_amu.iut.validation.model.ObservationAnalyse;
import fr.univ_amu.iut.validation.model.StatutObservation;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.util.StringConverter;

/// Catalogue des **critères de filtrage** de l'inventaire analyse (patron « à la Notion », #537). Chaque
/// critère est une entrée du menu « + Filtre » qui s'ajoute comme puce : **Statut** de revue et **Taxon
/// parent** (groupe, #518). La **recherche texte** permanente (espèce, carré) est fournie à part
/// ([#rechercheTexte()]).
///
/// Pendant, côté analyse, du `CriteresAudio` : de simples `CritereFiltre<ObservationAnalyse>` dont l'éditeur
/// pilote le prédicat du socle [fr.univ_amu.iut.commun.viewmodel.Filtres]. Aucune présélection sur les deux
/// puces : ajouter la puce n'écarte rien tant qu'une valeur n'est pas choisie (l'inventaire reste complet
/// par défaut, comme avant la barre à puces).
final class CriteresAnalyse {

    /// Clé **stable** du critère Statut, partagée par le critère et les vues par défaut (évite un littéral
    /// dupliqué).
    private static final String STATUT = "statut";

    private CriteresAnalyse() {}

    /// Vues **par défaut** (lecture seule) de l'inventaire analyse, rendues comme onglets avant les vues de
    /// l'utilisateur (#623), sur le modèle de `CriteresAudio` :
    /// - **« Tout »** (aucun filtre) : active au chargement, n'écarte rien ;
    /// - **« À valider »** (statut À revoir, le cœur de la revue) ;
    /// - **« Validées »** (statut Validée : ce qui est déjà traité) ;
    /// - **« Chiroptères »** (groupe Chiroptères, #471).
    ///
    /// Chaque descripteur est sérialisé exactement comme [GestionnaireFiltres#decrire()] le produirait, pour
    /// que rejouer la vue laisse un état « non modifié ».
    static List<VueSauvegardee> vuesParDefaut() {
        return List.of(
                vueParDefaut("Tout"),
                vueParDefaut(
                        "À valider", new DescripteurCritere(STATUT, List.of(StatutObservation.NON_TOUCHEE.name()))),
                vueParDefaut("Validées", new DescripteurCritere(STATUT, List.of(StatutObservation.VALIDEE.name()))),
                vueParDefaut("Chiroptères", new DescripteurCritere("groupe", List.of("Chiroptères"))));
    }

    /// Une vue par défaut : `id` **nul** (jamais persistée → lecture seule) et descripteur des critères donnés
    /// (aucun critère = vue « Tout », sans filtre).
    private static VueSauvegardee vueParDefaut(String nom, DescripteurCritere... criteres) {
        String descripteur = DescripteurFiltreJson.serialiser(new DescripteurFiltre("", List.of(criteres)));
        return new VueSauvegardee(null, "analyse", nom, descripteur);
    }

    /// Critère **Statut de revue** : éditeur = liste déroulante (Non touchée / Validée / Corrigée…) dans la
    /// puce, **sans présélection** (aucun filtre tant qu'un statut n'est pas choisi).
    static CritereFiltre<ObservationAnalyse> statut() {
        return new CritereFiltre<ObservationAnalyse>() {
            @Override
            public String nom() {
                return STATUT;
            }

            @Override
            public String libelle() {
                return "Statut";
            }

            @Override
            public Node editeur(Consumer<Predicate<ObservationAnalyse>> applique) {
                ComboBox<StatutObservation> choix = new ComboBox<>();
                choix.getItems().setAll(StatutObservation.values());
                choix.setPromptText("Choisir un statut");
                choix.setConverter(convertisseur(FormatAnalyse::libelleStatut));
                choix.valueProperty()
                        .addListener((obs, avant, statut) ->
                                applique.accept(statut == null ? o -> true : o -> o.statut() == statut));
                applique.accept(o -> true); // pas de présélection : n'écarte rien tant qu'un statut n'est pas choisi
                return choix;
            }

            @Override
            public List<String> valeurCourante(Node editeur) {
                Object valeur = ((ComboBox<?>) editeur).getValue();
                return valeur == null ? List.of() : List.of(((StatutObservation) valeur).name());
            }

            @Override
            public void restaurerValeurs(Node editeur, List<String> valeurs) {
                if (!valeurs.isEmpty()) {
                    selectionnerParValeur(editeur, StatutObservation.valueOf(valeurs.get(0)));
                }
            }
        };
    }

    /// Critère **Taxon parent** (groupe, #518) : éditeur = liste déroulante des groupes **présents dans
    /// l'inventaire** (fournis par `groupesPresents`, lus à l'ajout de la puce), **sans présélection**.
    static CritereFiltre<ObservationAnalyse> groupe(Supplier<? extends List<String>> groupesPresents) {
        return new CritereFiltre<ObservationAnalyse>() {
            @Override
            public String nom() {
                return "groupe";
            }

            @Override
            public String libelle() {
                return "Taxon parent";
            }

            @Override
            public Node editeur(Consumer<Predicate<ObservationAnalyse>> applique) {
                ComboBox<String> choix = new ComboBox<>();
                choix.getItems().setAll(groupesPresents.get());
                choix.setPromptText("Choisir un taxon parent");
                choix.valueProperty()
                        .addListener((obs, avant, groupe) ->
                                applique.accept(groupe == null ? o -> true : o -> groupe.equals(o.groupe())));
                applique.accept(o -> true); // pas de présélection : n'écarte rien tant qu'un groupe n'est pas choisi
                return choix;
            }

            @Override
            public List<String> valeurCourante(Node editeur) {
                Object valeur = ((ComboBox<?>) editeur).getValue();
                return valeur == null ? List.of() : List.of((String) valeur);
            }

            @Override
            public void restaurerValeurs(Node editeur, List<String> valeurs) {
                if (!valeurs.isEmpty()) {
                    selectionnerParValeur(editeur, valeurs.get(0));
                }
            }
        };
    }

    /// **Recherche texte** de la barre : vrai si un des champs cherchables d'une observation (taxon retenu,
    /// nom vernaculaire, nom latin, n° de carré, nom de site) contient l'aiguille (insensible casse/accents).
    /// Fournie au [fr.univ_amu.iut.commun.view.GestionnaireFiltres], qui l'applique au champ permanent.
    static BiPredicate<ObservationAnalyse, String> rechercheTexte() {
        return CriteresAnalyse::correspond;
    }

    private static boolean correspond(ObservationAnalyse observation, String texte) {
        String aiguille = NormalisationTexte.normaliser(texte);
        return contient(observation.taxonRetenu(), aiguille)
                || contient(observation.nomVernaculaireFr(), aiguille)
                || contient(observation.nomLatin(), aiguille)
                || contient(observation.numeroCarre(), aiguille)
                || contient(observation.nomSite(), aiguille);
    }

    private static boolean contient(String champ, String aiguille) {
        return champ != null && NormalisationTexte.normaliser(champ).contains(aiguille);
    }

    /// Sélectionne dans une liste déroulante l'élément **égal** à `valeur` (ou vide la sélection s'il est
    /// absent : `indexOf` → -1), pour restaurer une valeur mémorisée **sans cast générique non vérifié**.
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
