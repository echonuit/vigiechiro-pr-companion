package fr.univ_amu.iut.commun.view;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javafx.scene.Node;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.MenuButton;

/// Fabrique de **critères de filtre sur une dimension textuelle** (le carré, le point, la catégorie de
/// taxon…), partagée par les écrans qui offrent une barre de filtres.
///
/// ## Pourquoi le choix multiple
///
/// Une puce à valeur unique ne sait dire que « ce carré-ci ». Elle ne sait pas dire « ces trois
/// carrés », ni surtout **« tout sauf les chiroptères »** — l'onglet « Autres » que réclament les vues
/// d'espèces, où le détecteur rapporte aussi des orthoptères et des micromammifères (#2615).
///
/// Le socle n'était pourtant pas en cause : [CritereFiltre#editeur] porte déjà un `Predicate` **quelconque**,
/// et [DescripteurCritere#valeurs] est déjà une **liste**, donc une sélection multiple se mémorise et se
/// rejoue sans rien changer au contrat. Ce qui manquait était l'**éditeur**, que chaque écran écrivait
/// pour lui-même en liste déroulante à choix unique.
///
/// ## Sémantique
///
/// **Rien de coché n'écarte rien** : une puce fraîchement ajoutée ne doit pas vider la vue avant qu'on
/// ait choisi. Dès qu'une valeur est cochée, le prédicat devient une **appartenance** — la ligne passe si
/// sa dimension figure parmi les valeurs retenues.
///
/// @param <T> type des lignes filtrées
public final class CritereListe {

    private CritereListe() {}

    /// Un critère dont l'éditeur laisse cocher **plusieurs** valeurs parmi celles réellement présentes.
    ///
    /// @param cle clé stable du critère, partagée entre vues (elle sert aussi aux vues mémorisées)
    /// @param libelle intitulé de la puce
    /// @param invite texte du bouton tant que rien n'est coché
    /// @param valeursPresentes les valeurs offertes, calculées au moment où la puce s'ouvre
    /// @param dimension ce qu'on lit sur une ligne pour la comparer
    public static <T> CritereFiltre<T> multiple(
            String cle,
            String libelle,
            String invite,
            Supplier<? extends List<String>> valeursPresentes,
            Function<T, String> dimension) {
        return new CritereFiltre<T>() {
            @Override
            public String nom() {
                return cle;
            }

            @Override
            public String libelle() {
                return libelle;
            }

            @Override
            public Node editeur(Consumer<Predicate<T>> applique) {
                MenuButton bouton = new MenuButton(invite);
                bouton.getStyleClass().add("critere-multiple");
                for (String valeur : valeursPresentes.get()) {
                    CheckMenuItem item = new CheckMenuItem(valeur);
                    item.selectedProperty().addListener((obs, avant, coche) -> {
                        majLibelle(bouton, invite);
                        applique.accept(predicat(bouton, dimension));
                    });
                    bouton.getItems().add(item);
                }
                // Aucune présélection : tant que rien n'est coché, la puce n'écarte rien.
                applique.accept(ligne -> true);
                return bouton;
            }

            @Override
            public List<String> valeurCourante(Node editeur) {
                return cochees((MenuButton) editeur);
            }

            @Override
            public void restaurerValeurs(Node editeur, List<String> valeurs) {
                MenuButton bouton = (MenuButton) editeur;
                Set<String> voulues = new LinkedHashSet<>(valeurs);
                for (var item : bouton.getItems()) {
                    if (item instanceof CheckMenuItem coche) {
                        coche.setSelected(voulues.contains(coche.getText()));
                    }
                }
            }
        };
    }

    /// Le prédicat courant : appartenance aux valeurs cochées, ou **tout passe** si rien ne l'est.
    private static <T> Predicate<T> predicat(MenuButton bouton, Function<T, String> dimension) {
        List<String> retenues = cochees(bouton);
        if (retenues.isEmpty()) {
            return ligne -> true;
        }
        Set<String> ensemble = new LinkedHashSet<>(retenues);
        return ligne -> ensemble.contains(dimension.apply(ligne));
    }

    private static List<String> cochees(MenuButton bouton) {
        List<String> valeurs = new ArrayList<>();
        for (var item : bouton.getItems()) {
            if (item instanceof CheckMenuItem coche && coche.isSelected()) {
                valeurs.add(coche.getText());
            }
        }
        return List.copyOf(valeurs);
    }

    /// Le bouton dit **ce qui est retenu**, pas seulement qu'un filtre existe : une puce qui afficherait
    /// toujours « Taxon parent » obligerait à la déplier pour savoir ce qu'elle fait.
    private static void majLibelle(MenuButton bouton, String invite) {
        List<String> retenues = cochees(bouton);
        if (retenues.isEmpty()) {
            bouton.setText(invite);
        } else if (retenues.size() == 1) {
            bouton.setText(retenues.get(0));
        } else {
            bouton.setText(retenues.size() + " sélectionnés");
        }
    }
}
