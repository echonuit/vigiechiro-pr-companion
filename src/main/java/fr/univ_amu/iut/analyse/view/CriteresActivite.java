package fr.univ_amu.iut.analyse.view;

import fr.univ_amu.iut.analyse.model.ContactHoraire;
import fr.univ_amu.iut.commun.model.NormalisationTexte;
import fr.univ_amu.iut.commun.model.VueSauvegardee;
import fr.univ_amu.iut.commun.view.CritereFiltre;
import fr.univ_amu.iut.commun.view.DescripteurCritere;
import fr.univ_amu.iut.commun.view.VuesParDefaut;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;

/// Catalogue des **critères de filtrage** de la vue Activité (patron « à la Notion », socle
/// [fr.univ_amu.iut.commun.viewmodel.Filtres]), pendant du [CriteresAnalyse] mais sur [ContactHoraire].
/// Deux puces cascadables — **Carré** et **Taxon parent** (groupe) — plus la recherche texte permanente.
///
/// La sélection d'**espèce** n'est **pas** un filtre ici : elle vit dans les cases à cocher de la courbe
/// (quelles courbes tracer), pas dans le sous-ensemble de données. Aucune présélection : ajouter une puce
/// n'écarte rien tant qu'une valeur n'est pas choisie.
final class CriteresActivite {

    /// Clé du critère « Taxon parent », partagée par le filtre et les onglets par défaut.
    private static final String GROUPE = "groupe";

    private CriteresActivite() {}

    /// Critère **Carré** : liste déroulante des carrés présents (fournis par `carresPresents`, lus à l'ajout
    /// de la puce), sans présélection. Tête de la cascade carré → point → nuit.
    static CritereFiltre<ContactHoraire> carre(Supplier<? extends List<String>> carresPresents) {
        return liste("carre", "Carré", "Choisir un carré", carresPresents, ContactHoraire::numeroCarre);
    }

    /// Critère **Point** d'écoute : liste déroulante des points présents, sans présélection.
    static CritereFiltre<ContactHoraire> point(Supplier<? extends List<String>> pointsPresents) {
        return liste("point", "Point", "Choisir un point", pointsPresents, ContactHoraire::codePoint);
    }

    /// Critère **Nuit** (une nuit = un passage) : liste déroulante des nuits présentes (dates du soir),
    /// sans présélection.
    static CritereFiltre<ContactHoraire> nuit(Supplier<? extends List<String>> nuitsPresentes) {
        return liste("nuit", "Nuit", "Choisir une nuit", nuitsPresentes, CriteresActivite::libelleNuit);
    }

    /// Critère **Taxon parent** (groupe) : liste déroulante des groupes présents, sans présélection.
    static CritereFiltre<ContactHoraire> groupe(Supplier<? extends List<String>> groupesPresents) {
        return liste(GROUPE, "Taxon parent", "Choisir un taxon parent", groupesPresents, ContactHoraire::groupe);
    }

    private static String libelleNuit(ContactHoraire contact) {
        return contact.nuit() == null ? null : contact.nuit().toString();
    }

    /// Fabrique d'un critère « liste déroulante sur une dimension texte » : `ComboBox` peuplée à l'ouverture
    /// par `valeursPresentes`, qui restreint les contacts à ceux dont `dimension` égale la valeur choisie.
    private static CritereFiltre<ContactHoraire> liste(
            String cle,
            String titre,
            String invite,
            Supplier<? extends List<String>> valeursPresentes,
            Function<ContactHoraire, String> dimension) {
        return new CritereFiltre<>() {
            @Override
            public String nom() {
                return cle;
            }

            @Override
            public String libelle() {
                return titre;
            }

            @Override
            public Node editeur(Consumer<Predicate<ContactHoraire>> applique) {
                ComboBox<String> choix = new ComboBox<>();
                choix.getItems().setAll(valeursPresentes.get());
                choix.setPromptText(invite);
                choix.valueProperty()
                        .addListener((obs, avant, valeur) ->
                                applique.accept(valeur == null ? c -> true : c -> valeur.equals(dimension.apply(c))));
                applique.accept(c -> true); // pas de présélection : n'écarte rien tant qu'aucune valeur n'est choisie
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
                    ComboBox<?> choix = (ComboBox<?>) editeur;
                    choix.getSelectionModel().select(choix.getItems().indexOf(valeurs.get(0)));
                }
            }
        };
    }

    /// Onglets **par défaut** de l'écran, rendus en lecture seule avant les vues de l'utilisateur : ils
    /// partitionnent les taxons détectés par **catégorie du référentiel** (niveau `Catégorie` de
    /// `taxonomic_group`, semé par V05).
    ///
    /// Tadarida ne détecte pas que des chauves-souris : sur une vraie saison, orthoptères, autres
    /// mammifères et oiseaux figurent au même rang que les chiroptères, et la présélection des cinq taxons
    /// les plus contactés peut retenir une sauterelle — tracée alors comme une espèce de chauve-souris.
    /// Chaque onglet porte le **nom exact** de sa catégorie au référentiel : un onglet « Autres » qui ne
    /// couvrirait qu'une catégorie mentirait sur son contenu. Un vrai complément (tout sauf les
    /// chiroptères) demanderait un critère à choix multiple ou négatif, que le socle n'offre pas encore.
    static List<VueSauvegardee> vuesParDefaut() {
        return List.of(
                vueParDefaut("Tout"),
                vueParDefaut("Chiroptères", new DescripteurCritere(GROUPE, List.of("Chiroptères"))),
                vueParDefaut(
                        "Orthoptères et cigales", new DescripteurCritere(GROUPE, List.of("Orthoptères et cigales"))),
                vueParDefaut("Autres mammifères", new DescripteurCritere(GROUPE, List.of("Autres mammifères"))));
    }

    private static VueSauvegardee vueParDefaut(String nom, DescripteurCritere... criteres) {
        return VuesParDefaut.vue("activite", nom, criteres);
    }

    /// **Recherche texte** de la barre : vrai si un champ cherchable d'un contact (taxon retenu, nom
    /// vernaculaire, n° de carré, code point) contient l'aiguille (insensible casse/accents). Fournie au
    /// [fr.univ_amu.iut.commun.view.GestionnaireFiltres], qui l'applique au champ permanent.
    static BiPredicate<ContactHoraire, String> rechercheTexte() {
        return CriteresActivite::correspond;
    }

    private static boolean correspond(ContactHoraire contact, String texte) {
        String aiguille = NormalisationTexte.normaliser(texte);
        return contient(contact.taxon(), aiguille)
                || contient(contact.nomEspece(), aiguille)
                || contient(contact.numeroCarre(), aiguille)
                || contient(contact.codePoint(), aiguille);
    }

    private static boolean contient(String champ, String aiguille) {
        return champ != null && NormalisationTexte.normaliser(champ).contains(aiguille);
    }
}
