package fr.univ_amu.iut.analyse.view;

import fr.univ_amu.iut.analyse.model.ContactHoraire;
import fr.univ_amu.iut.commun.model.NormalisationTexte;
import fr.univ_amu.iut.commun.view.CritereFiltre;
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

    private CriteresActivite() {}

    /// Critère **Carré** : liste déroulante des carrés présents (fournis par `carresPresents`, lus à l'ajout
    /// de la puce), sans présélection.
    static CritereFiltre<ContactHoraire> carre(Supplier<? extends List<String>> carresPresents) {
        return liste("carre", "Carré", "Choisir un carré", carresPresents, ContactHoraire::numeroCarre);
    }

    /// Critère **Taxon parent** (groupe) : liste déroulante des groupes présents, sans présélection.
    static CritereFiltre<ContactHoraire> groupe(Supplier<? extends List<String>> groupesPresents) {
        return liste("groupe", "Taxon parent", "Choisir un taxon parent", groupesPresents, ContactHoraire::groupe);
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
