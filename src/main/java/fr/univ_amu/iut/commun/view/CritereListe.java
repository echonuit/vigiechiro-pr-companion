package fr.univ_amu.iut.commun.view;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
        return multipleParmi(cle, libelle, invite, valeursPresentes, dimensions, SANS_RATTRAPAGE);
    }

    /// Comment une valeur **mémorisée** retrouve son entrée quand aucune ne porte exactement son texte.
    ///
    /// Une vue sauvegardée persiste les valeurs cochées **en clair** ([DescripteurCritere#valeurs]).
    /// Requalifier une entrée (« Z1 » devenu « 640380 · Z1 », #2992) rend donc introuvables toutes les
    /// valeurs enregistrées avant elle. Depuis #3093 la perte est **annoncée**, ce qui vaut mieux qu'un
    /// élargissement silencieux ; elle le serait néanmoins à chaque rejeu, pour un changement dont
    /// l'utilisateur n'est pas l'auteur.
    ///
    /// Le rattrapage est ce qui manquait : la règle par laquelle une ancienne valeur désigne encore son
    /// entrée. Elle appartient au critère, non au socle, parce qu'elle dépend de la **forme** des
    /// valeurs : « 640380 » désigne « 640380 · Vallon » quand un lieu se qualifie par son carré, là où
    /// un taxon parent ne se rattrape par rien.
    @FunctionalInterface
    public interface Rattrapage {

        /// L'entrée que `memorisee` désigne encore, ou **vide** si aucune ne convient - **ou si
        /// plusieurs conviennent**.
        ///
        /// Le second cas est le plus important : deviner entre deux entrées reviendrait à filtrer sur un
        /// lieu que l'utilisateur n'a pas choisi. Rendre vide laisse #3093 dire ce qui n'a pas été
        /// replacé, et l'utilisateur trancher lui-même.
        ///
        /// Le critère répond depuis **son propre domaine**, que le socle ne connaît pas : ce qui
        /// distingue « 640380 · Vallon » de « 640380 · A1 » n'est pas leur texte, c'est la **dimension**
        /// dont chacune vient. Une proposition qui ne serait pas offerte par le menu est écartée par
        /// l'appelant.
        Optional<String> retrouver(String memorisee);
    }

    /// Rattrapage **nul**, celui de tous les critères qui n'en déclarent pas : une valeur mémorisée se
    /// replace sur son texte exact, ou pas du tout.
    public static final Rattrapage SANS_RATTRAPAGE = memorisee -> Optional.empty();

    /// La même fabrique, avec la règle par laquelle une valeur **mémorisée avant un renommage** retrouve
    /// son entrée (#3158).
    ///
    /// @param rattrapage consulté **seulement** quand aucune entrée ne porte exactement la valeur
    ///     mémorisée : le texte exact prime toujours
    public static <T> CritereFiltre<T> multipleParmi(
            String cle,
            String libelle,
            String invite,
            Supplier<? extends List<GroupeValeurs>> valeursPresentes,
            Function<T, ? extends Collection<String>> dimensions,
            Rattrapage rattrapage) {
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
                peupler(bouton, invite, valeursPresentes.get(), applique, dimensions);
                // Cascadage (#3095) : le domaine se recalcule **à l'ouverture** du menu, et non à chaque
                // changement de filtre. C'est le seul instant où la liste est regardée, donc le seul où
                // son exactitude compte ; recalculer plus tôt coûterait à chaque frappe pour rien.
                bouton.setOnShowing(evenement -> peupler(bouton, invite, valeursPresentes.get(), applique, dimensions));
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
                Map<String, String> cibles = cibles(bouton, valeurs, rattrapage);
                Set<String> aCocher = new LinkedHashSet<>(cibles.values());
                for (var item : bouton.getItems()) {
                    if (item instanceof CheckMenuItem coche) {
                        coche.setSelected(aCocher.contains(coche.getText()));
                    }
                }
                return valeurs.stream()
                        .filter(valeur -> !cibles.containsKey(valeur))
                        .toList();
            }
        };
    }

    /// Ce qu'un critère de liste manipule vraiment : un **domaine de valeurs**.
    ///
    /// Trois écrans de ce dépôt écrivaient la même liste déroulante sur trois types différents - une
    /// énumération, un `String`, un record `EspecePresente` - et l'écart tenait à **deux fonctions**
    /// seulement : ce qu'on **voit** et ce qu'on **mémorise**. Les nommer ensemble donne un objet qui se
    /// passe d'un appel à l'autre, plutôt que trois paramètres de plus à chaque fabrique.
    ///
    /// La distinction libellé / clé n'est pas cosmétique. Le **libellé** peut changer (traduction,
    /// reformulation, #2967) sans rien casser ; la **clé** est le contrat de sérialisation des vues
    /// mémorisées, et la changer les rendrait toutes caduques.
    ///
    /// @param valeurs les valeurs offertes, calculées au moment où la puce s'ouvre
    /// @param libelle ce que l'utilisateur lit dans la liste
    /// @param cle ce qu'une vue mémorisée enregistre, et par quoi elle retrouve la valeur
    public record Domaine<T>(
            Supplier<? extends List<T>> valeurs, Function<T, String> libelle, Function<T, String> cle) {

        /// Domaine de **chaînes** : ce qu'on voit et ce qu'on mémorise sont la valeur elle-même.
        public static Domaine<String> deChaines(Supplier<? extends List<String>> valeurs) {
            return new Domaine<>(valeurs, valeur -> valeur, valeur -> valeur);
        }

        /// Domaine d'une **énumération** : la clé est le `name()`, **jamais** le libellé.
        public static <E extends Enum<E>> Domaine<E> deConstantes(List<E> valeurs, Function<E, String> libelle) {
            return new Domaine<>(() -> valeurs, libelle, Enum::name);
        }
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
        return valeurs(cle, libelle, invite, Domaine.deConstantes(valeurs, libelleValeur), predicat);
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
        return valeursPreselectionnees(
                cle, libelle, Domaine.deConstantes(valeurs, libelleValeur), predicat, liste -> valeurParDefaut);
    }

    /// Un critère dont l'éditeur déroule les valeurs d'un [Domaine] : la fabrique dont [#simple] et
    /// [#enumeration] sont des cas particuliers.
    ///
    /// **Aucune présélection** : ajouter la puce n'écarte rien tant qu'une valeur n'est pas choisie.
    /// L'écran qui a besoin du contraire passe par [#valeursPreselectionnees], dont le nom rend
    /// l'exception visible à l'appel plutôt que dans un paramètre nul.
    ///
    /// @param invite texte affiché tant que rien n'est choisi
    /// @param predicat ce que retenir une valeur veut dire pour cet écran
    public static <L, T> CritereFiltre<L> valeurs(
            String cle, String libelle, String invite, Domaine<T> domaine, Function<T, Predicate<L>> predicat) {
        return construire(cle, libelle, invite, domaine, predicat, null, valeur -> {});
    }

    /// Comme [#valeurs], mais avec une valeur **présélectionnée** : la puce filtre dès qu'on l'ajoute.
    ///
    /// La valeur par défaut est calculée **sur les valeurs offertes** et non fixée d'avance : la revue des
    /// sons s'ouvre sur « Chiroptères » *s'il est présent*, et sur le premier groupe sinon. Un défaut
    /// constant y aurait rendu une puce vide les jours sans chiroptère.
    ///
    /// @param valeurParDefaut la valeur appliquée dès l'ajout, choisie parmi celles offertes
    public static <L, T> CritereFiltre<L> valeursPreselectionnees(
            String cle,
            String libelle,
            Domaine<T> domaine,
            Function<T, Predicate<L>> predicat,
            Function<List<T>, T> valeurParDefaut) {
        return construire(cle, libelle, null, domaine, predicat, valeurParDefaut, valeur -> {});
    }

    /// Variante qui **annonce** le remplacement d'un choix devenu impossible (#3095).
    ///
    /// Quand le domaine se recalcule et que la valeur retenue n'y figure plus, le défaut reprend la
    /// main plutôt que de laisser la puce sans choix. L'écran filtre alors sur autre chose que ce qui
    /// avait été demandé : `auBasculement` reçoit la valeur perdue pour que l'écran le dise, faute de
    /// quoi la table changerait sous les yeux sans raison lisible.
    public static <L, T> CritereFiltre<L> valeursPreselectionnees(
            String cle,
            String libelle,
            Domaine<T> domaine,
            Function<T, Predicate<L>> predicat,
            Function<List<T>, T> valeurParDefaut,
            Consumer<String> auBasculement) {
        return construire(cle, libelle, null, domaine, predicat, valeurParDefaut, auBasculement);
    }

    private static <L, T> CritereFiltre<L> construire(
            String cle,
            String libelle,
            String invite,
            Domaine<T> domaine,
            Function<T, Predicate<L>> predicat,
            Function<List<T>, T> valeurParDefaut,
            Consumer<String> auBasculement) {
        return new CritereFiltre<L>() {

            /// Les valeurs **réellement montrées** par l'éditeur, telles qu'il les a reçues.
            ///
            /// Gardées ici plutôt que relues du domaine à chaque appel : le fournisseur dépend des lignes
            /// courantes, et une relecture pourrait rendre une autre liste que celle qu'on a sous les yeux.
            ///
            /// Elles servent à **retyper** la valeur choisie, que l'éditeur ne rend que sous forme
            /// d'`Object` : c'est le seul moyen d'appliquer [Domaine#cle()] sans transtypage non vérifié.
            /// Elles ne servent **plus** à la désigner par son rang (#3128) : une valeur se retrouve par
            /// ce qu'elle est. Lire par position supposait que cette liste et celle affichée restent
            /// alignées, ce qui tenait tant qu'aucune des deux ne bougeait ; le jour où le domaine se
            /// recalcule (#3095), le rang désigne une autre valeur et la vue enregistrée rejoue un autre
            /// filtre, sans que rien ne casse bruyamment.
            ///
            /// **Invariant à tenir** quand les domaines deviendront cascadés : cette liste et les entrées
            /// de l'éditeur se refont **ensemble, depuis la même source**. Une valeur affichée qui n'y
            /// figurerait pas cesserait d'être lisible.
            private List<T> offertes = List.of();

            @Override
            public String nom() {
                return cle;
            }

            @Override
            public String libelle() {
                return libelle;
            }

            /// Recalcule le domaine et **préserve le choix** s'il y figure encore (#3095).
            ///
            /// S'il n'y figure plus, deux comportements selon le critère. Sans défaut, la sélection est
            /// vidée : la puce cesse d'écarter quoi que ce soit, ce qui est la sémantique du socle. Avec
            /// défaut, le défaut **reprend la main** plutôt que de laisser la puce sans choix.
            ///
            /// Ce second cas a une conséquence qu'il faut assumer : l'écran filtre alors sur autre chose
            /// que ce qui avait été demandé. C'est le mode de panne que #3056 et #3093 ont corrigé
            /// ailleurs, d'où l'annonce - réappliquer était la décision, le taire n'en faisait pas
            /// partie.
            private void rafraichir(ComboBox<T> choix) {
                String choisie = cleDe(choix.getValue());
                offertes = List.copyOf(domaine.valeurs().get());
                choix.getItems().setAll(offertes);
                T retrouvee = offertes.stream()
                        .filter(valeur -> domaine.cle().apply(valeur).equals(choisie))
                        .findFirst()
                        .orElse(null);
                if (choisie == null || retrouvee != null) {
                    choix.setValue(retrouvee);
                    return;
                }
                choix.setValue(valeurParDefaut == null ? null : valeurParDefaut.apply(offertes));
                if (valeurParDefaut != null) {
                    auBasculement.accept(choisie);
                }
            }

            /// La clé de `valeur`, ou `null` si rien n'est choisi. Retypée via [#offertes], seul moyen
            /// d'appliquer [Domaine#cle()] sans transtypage non vérifié (cf. #3128).
            private String cleDe(Object valeur) {
                return offertes.stream()
                        .filter(offerte -> offerte.equals(valeur))
                        .findFirst()
                        .map(domaine.cle())
                        .orElse(null);
            }

            @Override
            public Node editeur(Consumer<Predicate<L>> applique) {
                ComboBox<T> choix = new ComboBox<>();
                offertes = List.copyOf(domaine.valeurs().get());
                choix.getItems().setAll(offertes);
                choix.setPromptText(invite);
                choix.setConverter(new StringConverter<T>() {
                    @Override
                    public String toString(T valeur) {
                        return valeur == null ? "" : domaine.libelle().apply(valeur);
                    }

                    @Override
                    public T fromString(String texte) {
                        return null; // liste non éditable au clavier
                    }
                });
                choix.valueProperty()
                        .addListener((obs, avant, valeur) ->
                                applique.accept(valeur == null ? ligne -> true : predicat.apply(valeur)));
                if (valeurParDefaut == null) {
                    applique.accept(ligne -> true);
                } else {
                    choix.setValue(valeurParDefaut.apply(offertes)); // déclenche l'application initiale
                }
                // Cascadage (#3095) : le domaine se recalcule à l'ouverture de la liste, seul instant où
                // elle est regardée.
                choix.setOnShowing(evenement -> rafraichir(choix));
                return choix;
            }

            @Override
            public List<String> valeurCourante(Node editeur) {
                Object choisie = ((ComboBox<?>) editeur).getValue();
                return offertes.stream()
                        .filter(valeur -> valeur.equals(choisie))
                        .findFirst()
                        .map(valeur -> List.of(domaine.cle().apply(valeur)))
                        .orElseGet(List::of);
            }

            @Override
            public List<String> restaurerValeurs(Node editeur, List<String> memorisees) {
                if (memorisees.isEmpty()) {
                    return List.of();
                }
                // Retrouvée par sa CLÉ parmi les valeurs offertes, et non reconstruite : une clé disparue
                // (constante retirée, espèce absente du jeu du jour) doit rejouer la vue sans filtre plutôt
                // que faire échouer l'écran - mais elle se DIT, sans quoi la vue filtre moins qu'annoncé
                // (#3056).
                int indice = offertes.stream().map(domaine.cle()).toList().indexOf(memorisees.get(0));
                ((ComboBox<?>) editeur).getSelectionModel().select(indice);
                return indice < 0 ? List.of(memorisees.get(0)) : List.of();
            }
        };
    }

    /// Classe de style d'une valeur **cochée mais absente du jeu courant** (#3095) : elle filtre encore,
    /// mais ne ramène aucune ligne. Le style vit dans `commun/view/design.css`.
    public static final String CLASSE_VALEUR_HORS_JEU = "valeur-hors-jeu";

    /// (Re)construit les entrées du menu à partir des `groupes` offerts, en **conservant la sélection**.
    ///
    /// Les valeurs cochées qui ne figurent plus dans le domaine ne sont **pas** retirées : elles sont
    /// rendues en fin de liste, toujours cochées, marquées par [#CLASSE_VALEUR_HORS_JEU]. Les retirer
    /// relâcherait le filtre **en silence**, et l'écran montrerait alors plus que ce qu'il annonce : le
    /// défaut même que #3056 et #3093 ont corrigé ailleurs. Les garder visibles répond aussi à la
    /// question qu'on se pose devant une table vide, « pourquoi n'y a-t-il rien ? ».
    private static <T> void peupler(
            MenuButton bouton,
            String invite,
            List<GroupeValeurs> groupes,
            Consumer<Predicate<T>> applique,
            Function<T, ? extends Collection<String>> dimensions) {
        List<String> retenues = cochees(bouton);
        bouton.getItems().clear();
        Set<String> offertes = new LinkedHashSet<>();
        for (GroupeValeurs groupe : groupes) {
            if (groupe.valeurs().isEmpty()) {
                continue; // un en-tête sans valeur ne renseigne sur rien
            }
            ajouterEnTete(bouton, groupe);
            for (String valeur : groupe.valeurs()) {
                offertes.add(valeur);
                bouton.getItems().add(entree(bouton, invite, valeur, retenues.contains(valeur), applique, dimensions));
            }
        }
        List<String> horsJeu =
                retenues.stream().filter(valeur -> !offertes.contains(valeur)).toList();
        if (!horsJeu.isEmpty()) {
            if (!bouton.getItems().isEmpty()) {
                bouton.getItems().add(new SeparatorMenuItem());
            }
            for (String valeur : horsJeu) {
                CheckMenuItem item = entree(bouton, invite, valeur, true, applique, dimensions);
                item.getStyleClass().add(CLASSE_VALEUR_HORS_JEU);
                bouton.getItems().add(item);
            }
        }
        majLibelle(bouton, invite);
    }

    private static void ajouterEnTete(MenuButton bouton, GroupeValeurs groupe) {
        if (groupe.titre() == null || groupe.titre().isBlank()) {
            return;
        }
        if (!bouton.getItems().isEmpty()) {
            bouton.getItems().add(new SeparatorMenuItem());
        }
        MenuItem entete = new MenuItem(groupe.titre());
        entete.setDisable(true); // en-tête : il nomme, il ne se coche pas
        entete.getStyleClass().add("entete-groupe-critere");
        bouton.getItems().add(entete);
    }

    /// Une entrée cochable, dont le changement d'état met à jour le libellé du bouton et republie le
    /// prédicat. L'écouteur est posé **après** l'état initial, pour que reconstruire la liste ne
    /// republie pas un prédicat identique à chaque ouverture du menu.
    private static <T> CheckMenuItem entree(
            MenuButton bouton,
            String invite,
            String valeur,
            boolean cochee,
            Consumer<Predicate<T>> applique,
            Function<T, ? extends Collection<String>> dimensions) {
        CheckMenuItem item = new CheckMenuItem(valeur);
        item.setSelected(cochee);
        item.selectedProperty().addListener((obs, avant, coche) -> {
            majLibelle(bouton, invite);
            applique.accept(predicat(bouton, dimensions));
        });
        return item;
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

    /// Ce que chaque valeur mémorisée désigne dans le menu : **elle-même** quand son entrée existe
    /// encore, ce que le [Rattrapage] retrouve sinon. Une valeur absente de la table n'a rien trouvé, et
    /// c'est elle que [CritereFiltre#restaurerValeurs] rend à son appelant (#3056, #3093).
    ///
    /// Deux valeurs mémorisées peuvent viser la **même** entrée (« 640380 » et « Vallon » après leur
    /// fusion) : la table les garde toutes deux, pour qu'aucune ne soit rendue comme perdue, et le menu
    /// ne coche l'entrée qu'une fois.
    private static Map<String, String> cibles(MenuButton bouton, List<String> valeurs, Rattrapage rattrapage) {
        List<String> entrees = entrees(bouton);
        Map<String, String> cibles = new LinkedHashMap<>();
        for (String valeur : valeurs) {
            if (entrees.contains(valeur)) {
                cibles.put(valeur, valeur);
            } else {
                // Une entrée que le menu n'offre pas ne se coche pas : le rattrapage propose, le menu
                // dispose.
                rattrapage.retrouver(valeur).filter(entrees::contains).ifPresent(entree -> cibles.put(valeur, entree));
            }
        }
        return cibles;
    }

    /// Les valeurs actuellement **offertes** par le menu, en-têtes et séparateurs écartés.
    private static List<String> entrees(MenuButton bouton) {
        return bouton.getItems().stream()
                .filter(CheckMenuItem.class::isInstance)
                .map(MenuItem::getText)
                .toList();
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
