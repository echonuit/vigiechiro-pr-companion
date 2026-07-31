package fr.univ_amu.iut.commun.view;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javafx.scene.Node;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.util.StringConverter;

/// Fabrique de **critères de filtre sur une dimension textuelle** (le carré, le point, la catégorie de
/// taxon…), partagée par les écrans qui offrent une barre de filtres.
///
/// ## Pourquoi le choix multiple
///
/// Une puce à valeur unique ne sait dire que « ce carré-ci ». Elle ne sait pas dire « ces trois
/// carrés », ni surtout **« tout sauf les chiroptères »** : l'onglet « Autres » que réclament les vues
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
/// ait choisi. Dès qu'une valeur est cochée, le prédicat devient une **appartenance** : la ligne passe si
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
        // Un groupe SANS titre : `multiple` garde exactement son apparence de liste plate, sans
        // en-tête. Le groupement (#2992) ne sert qu'aux critères à plusieurs dimensions.
        Supplier<List<GroupeValeurs>> enUnGroupe =
                () -> List.of(new GroupeValeurs(null, List.copyOf(valeursPresentes.get())));
        return multipleParmi(cle, libelle, invite, enUnGroupe, (T ligne) -> {
            String valeur = dimension.apply(ligne);
            return valeur == null ? List.<String>of() : List.of(valeur);
        });
    }

    /// Un **groupe de valeurs** d'une liste à cocher : son titre et ses valeurs. Le titre est rendu en
    /// en-tête **non cliquable**, ce qui est tout l'objet du groupement.
    ///
    /// @param titre l'intitulé de l'en-tête (« Communes », « Carrés »…), ou `null` pour un groupe
    ///     **sans** en-tête, ce qui rend une liste plate comme avant le groupement
    /// @param valeurs les valeurs cochables du groupe, dans l'ordre d'affichage
    public record GroupeValeurs(String titre, List<String> valeurs) {}

    /// Variante de [#multiple] pour une ligne qui porte **plusieurs dimensions comparables** (le critère
    /// « Lieu » confronte commune, carré, point et site à la même liste, #2794) : la ligne passe si
    /// **l'une** de ses valeurs figure parmi celles cochées. Même sémantique de départ : rien de coché
    /// n'écarte rien. (Nom distinct : une surcharge de `multiple` aurait le même effacement.)
    ///
    /// **Les valeurs sont groupées** par dimension, chaque groupe précédé de son titre en en-tête non
    /// cliquable (#2992). Une liste plate mêlant communes, carrés et points ne dit pas de quelle nature
    /// est une entrée, et il faut la connaître pour choisir : « Ahetze » est-il une commune ou un site ?
    ///
    /// Les trois aides internes ([#cochees], [#majLibelle], [#predicat]) filtrent sur `CheckMenuItem` :
    /// en-têtes et séparateurs sont donc ignorés sans traitement particulier, et une valeur cochée reste
    /// mémorisable dans une vue exactement comme avant.
    ///
    /// @param dimensions ce qu'on lit sur une ligne pour la comparer (toutes les valeurs candidates)
    public static <T> CritereFiltre<T> multipleParmi(
            String cle,
            String libelle,
            String invite,
            Supplier<? extends List<GroupeValeurs>> valeursPresentes,
            Function<T, ? extends Collection<String>> dimensions) {
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
                for (GroupeValeurs groupe : valeursPresentes.get()) {
                    if (groupe.valeurs().isEmpty()) {
                        continue; // un en-tête sans valeur ne renseigne sur rien
                    }
                    if (groupe.titre() != null && !groupe.titre().isBlank()) {
                        if (!bouton.getItems().isEmpty()) {
                            bouton.getItems().add(new SeparatorMenuItem());
                        }
                        MenuItem entete = new MenuItem(groupe.titre());
                        entete.setDisable(true); // en-tête : il nomme, il ne se coche pas
                        entete.getStyleClass().add("entete-groupe-critere");
                        bouton.getItems().add(entete);
                    }
                    for (String valeur : groupe.valeurs()) {
                        CheckMenuItem item = new CheckMenuItem(valeur);
                        item.selectedProperty().addListener((obs, avant, coche) -> {
                            majLibelle(bouton, invite);
                            applique.accept(predicat(bouton, dimensions));
                        });
                        bouton.getItems().add(item);
                    }
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
            public List<String> restaurerValeurs(Node editeur, List<String> valeurs) {
                MenuButton bouton = (MenuButton) editeur;
                Set<String> voulues = new LinkedHashSet<>(valeurs);
                Set<String> replacees = new LinkedHashSet<>();
                for (var item : bouton.getItems()) {
                    if (item instanceof CheckMenuItem coche) {
                        boolean voulue = voulues.contains(coche.getText());
                        coche.setSelected(voulue);
                        if (voulue) {
                            replacees.add(coche.getText());
                        }
                    }
                }
                return valeurs.stream()
                        .filter(valeur -> !replacees.contains(valeur))
                        .toList();
            }
        };
    }

    /// Un critère dont l'éditeur laisse choisir **une seule** valeur : la liste déroulante classique, pour
    /// les dimensions qui se lisent une à une (un carré, une nuit, la nature d'une nuit). Même contrat et
    /// même sémantique de départ que [#multiple] (rien de choisi n'écarte rien), et mêmes paramètres,
    /// pour qu'une dimension puisse passer de l'une à l'autre sans réécrire son appel.
    ///
    /// @param cle clé stable du critère, partagée entre vues (elle sert aussi aux vues mémorisées)
    /// @param libelle intitulé de la puce
    /// @param invite texte affiché tant que rien n'est choisi
    /// @param valeursPresentes les valeurs offertes, calculées au moment où la puce s'ouvre
    /// @param dimension ce qu'on lit sur une ligne pour la comparer
    public static <T> CritereFiltre<T> simple(
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
                ComboBox<String> choix = new ComboBox<>();
                choix.getItems().setAll(valeursPresentes.get());
                choix.setPromptText(invite);
                choix.valueProperty()
                        .addListener((obs, avant, valeur) -> applique.accept(
                                valeur == null ? ligne -> true : ligne -> valeur.equals(dimension.apply(ligne))));
                // Aucune présélection : tant que rien n'est choisi, la puce n'écarte rien.
                applique.accept(ligne -> true);
                return choix;
            }

            @Override
            public List<String> valeurCourante(Node editeur) {
                Object valeur = ((ComboBox<?>) editeur).getValue();
                return valeur == null ? List.of() : List.of((String) valeur);
            }

            @Override
            public List<String> restaurerValeurs(Node editeur, List<String> valeurs) {
                if (valeurs.isEmpty()) {
                    return List.of();
                }
                ComboBox<?> choix = (ComboBox<?>) editeur;
                int indice = choix.getItems().indexOf(valeurs.get(0));
                choix.getSelectionModel().select(indice);
                return indice < 0 ? List.of(valeurs.get(0)) : List.of();
            }
        };
    }

    /// Un critère dont l'éditeur déroule une **énumération** : la liste déroulante de [#simple], mais dont
    /// les entrées sont les constantes d'un `enum` plutôt que des chaînes lues dans les données.
    ///
    /// Cinq critères la réécrivaient à la main (statut d'observation ×2, statut de workflow, verdict, état
    /// d'analyse), et se décrivaient tous par la même phrase. Ce que le socle ne savait pas faire n'était
    /// pas la liste : c'était **afficher** une constante autrement que par son `name()`, et **retrouver**
    /// cette constante au moment de rejouer une vue mémorisée. C'est tout ce que cette fabrique ajoute.
    ///
    /// La valeur mémorisée est le `name()` de la constante, jamais son libellé : traduire un intitulé
    /// casserait toutes les vues enregistrées.
    ///
    /// **Aucune présélection**, comme partout ailleurs dans ce socle : ajouter la puce n'écarte rien tant
    /// qu'une valeur n'est pas choisie. L'écran qui a besoin du contraire passe par
    /// [#enumerationPreselectionnee], dont le nom rend l'exception visible à l'appel.
    ///
    /// @param cle clé stable du critère, partagée entre vues (elle sert aussi aux vues mémorisées)
    /// @param libelle intitulé de la puce
    /// @param invite texte affiché tant que rien n'est choisi
    /// @param valeurs les constantes offertes, dans l'ordre d'affichage (l'appelant peut en écarter)
    /// @param libelleValeur l'intitulé lisible d'une constante, jamais son `name()`
    /// @param predicat ce que filtrer sur une constante veut dire pour cet écran
    public static <T, E extends Enum<E>> CritereFiltre<T> enumeration(
            String cle,
            String libelle,
            String invite,
            List<E> valeurs,
            Function<E, String> libelleValeur,
            Function<E, Predicate<T>> predicat) {
        return enumeration(cle, libelle, invite, valeurs, libelleValeur, predicat, null);
    }

    /// Comme [#enumeration], mais avec une valeur **présélectionnée** : la puce filtre dès qu'on l'ajoute.
    ///
    /// C'est l'exception, pas la règle, et elle porte un nom distinct pour que l'écart se lise **à
    /// l'appel** plutôt que dans un paramètre nul. Elle n'a de sens que là où la valeur par défaut est le
    /// geste de l'écran lui-même (la revue des sons s'ouvre sur « À revoir » : ouvrir sur tout obligerait
    /// à filtrer avant de commencer, à chaque fois).
    ///
    /// @param valeurParDefaut la constante appliquée dès l'ajout de la puce
    public static <T, E extends Enum<E>> CritereFiltre<T> enumerationPreselectionnee(
            String cle,
            String libelle,
            List<E> valeurs,
            Function<E, String> libelleValeur,
            Function<E, Predicate<T>> predicat,
            E valeurParDefaut) {
        return enumeration(cle, libelle, null, valeurs, libelleValeur, predicat, valeurParDefaut);
    }

    private static <T, E extends Enum<E>> CritereFiltre<T> enumeration(
            String cle,
            String libelle,
            String invite,
            List<E> valeurs,
            Function<E, String> libelleValeur,
            Function<E, Predicate<T>> predicat,
            E valeurParDefaut) {
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
                ComboBox<E> choix = new ComboBox<>();
                choix.getItems().setAll(valeurs);
                choix.setPromptText(invite);
                choix.setConverter(new StringConverter<E>() {
                    @Override
                    public String toString(E valeur) {
                        return valeur == null ? "" : libelleValeur.apply(valeur);
                    }

                    @Override
                    public E fromString(String texte) {
                        return null; // liste non éditable au clavier
                    }
                });
                choix.valueProperty()
                        .addListener((obs, avant, valeur) ->
                                applique.accept(valeur == null ? ligne -> true : predicat.apply(valeur)));
                if (valeurParDefaut == null) {
                    applique.accept(ligne -> true);
                } else {
                    choix.setValue(valeurParDefaut); // déclenche l'application initiale par l'écouteur
                }
                return choix;
            }

            @Override
            public List<String> valeurCourante(Node editeur) {
                Object valeur = ((ComboBox<?>) editeur).getValue();
                return valeur == null ? List.of() : List.of(((Enum<?>) valeur).name());
            }

            @Override
            public List<String> restaurerValeurs(Node editeur, List<String> memorisees) {
                if (memorisees.isEmpty()) {
                    return List.of();
                }
                // Retrouvée PARMI LES VALEURS OFFERTES, et non par Enum.valueOf : une constante disparue
                // de l'énumération ferait lever valueOf, et une vue mémorisée devenue caduque doit se
                // rejouer sans filtre plutôt que faire échouer l'écran. Sélection par INDICE, comme dans
                // [#simple] : elle ne demande pas de connaître le type de la liste, et -1 la vide.
                List<String> noms = valeurs.stream().map(Enum::name).toList();
                int indice = noms.indexOf(memorisees.get(0));
                ((ComboBox<?>) editeur).getSelectionModel().select(indice);
                // Se rejouer sans filtre reste le bon comportement ; le taire ne l'est pas (#3056).
                return indice < 0 ? List.of(memorisees.get(0)) : List.of();
            }
        };
    }

    /// Le prédicat courant : appartenance aux valeurs cochées, ou **tout passe** si rien ne l'est. Une
    /// ligne passe dès que **l'une** de ses valeurs candidates est cochée.
    private static <T> Predicate<T> predicat(MenuButton bouton, Function<T, ? extends Collection<String>> dimensions) {
        List<String> retenues = cochees(bouton);
        if (retenues.isEmpty()) {
            return ligne -> true;
        }
        Set<String> ensemble = new LinkedHashSet<>(retenues);
        return ligne -> dimensions.apply(ligne).stream().anyMatch(ensemble::contains);
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
