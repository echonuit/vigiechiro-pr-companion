package fr.univ_amu.iut.commun.view;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/// Fabrique du critère **« Lieu »** (#3097) : plusieurs dimensions géographiques confrontées à une même
/// liste à cocher.
///
/// Quatre catalogues écrivaient ce critère à l'identique, et `CriteresAnalyse` le disait déjà de
/// lui-même : « C'est la jumelle de `CriteresAudio.lieu` (#2794), dont elle reprend le libellé et
/// l'ordre ». Ce qui variait tenait à **une** chose : quelles dimensions l'écran offre.
///
/// ## Pourquoi les dimensions sont un paramètre
///
/// Elles varient réellement d'un écran à l'autre, en **nombre** comme en **composition** :
///
/// | Écran | Dimensions |
/// |---|---|
/// | Sons & validation | communes, carrés, points, sites |
/// | Espèces & observations | communes, carrés, **sites** |
/// | Activité de la nuit | communes, carrés, **points** |
/// | Carte & passages | communes, carrés, **points** |
///
/// Trois écrans sur quatre en offrent trois, et **pas les mêmes trois**. Aplatir cela derrière une
/// fabrique à quatre dimensions fixes ferait trancher une question d'usage par une commodité
/// d'implémentation ; la question, elle, est posée dans #3145 et attend sa réponse.
///
/// ## Sémantique
///
/// Une ligne passe si **l'une** de ses dimensions figure parmi les valeurs cochées. Rien de coché
/// n'écarte rien.
///
/// Les valeurs sont **groupées** par dimension, chaque groupe précédé de son titre en en-tête non
/// cliquable (#2992) : une liste plate mêlant communes, carrés et points ne dit pas de quelle nature
/// est une entrée, et il faut la connaître pour choisir. Un groupe **sans valeur** n'affiche pas son
/// en-tête, qui ne renseignerait sur rien et ferait croire à une liste tronquée.
public final class CritereLieu {

    /// Le séparateur d'une valeur **qualifiée** par ce qui la désambiguïse : « 640380 · A1 » (#2992).
    ///
    /// Trois features l'écrivent encore en dur dans leur `pointQualifie` (`CriteresAudio`,
    /// `CriteresMultisite`, `ContactHoraire`). Les y brancher demanderait de le remonter en
    /// `commun.model` : `ContactHoraire` est un modèle, et un modèle ne dépend pas d'une vue
    /// (`ArchitectureTest`). À faire avec #3157, qui qualifie le carré à son tour.
    public static final String SEPARATEUR = " · ";

    /// Rattrapage des valeurs mémorisées **avant** qualification (#3158) : « Z1 » désigne encore
    /// « 640380 · Z1 », puisque la qualification n'a rien changé au lieu, seulement à son écriture.
    ///
    /// Elle ne rattrape que si **une seule** entrée reprend la valeur. Sur les écrans qui couvrent la
    /// saison entière, « Z1 » existe dans presque tous les carrés : cocher le premier venu filtrerait
    /// sur un point que l'utilisateur n'a pas choisi. Le silence laisse #3093 le dire.
    private static final CritereListe.Rattrapage PAR_SEGMENT = CritereLieu::parSegment;

    private CritereLieu() {}

    /// Une **dimension** géographique offerte par le critère : son intitulé de groupe et ce qu'on lit
    /// sur une ligne.
    ///
    /// @param titre l'en-tête du groupe (« Communes », « Carrés »…), au pluriel comme les autres
    /// @param lecture ce que la dimension vaut pour une ligne, ou `null` si la ligne ne la porte pas
    public record Dimension<T>(String titre, Function<T, String> lecture) {}

    /// Le critère « Lieu » offrant `dimensions`, alimenté par `lignes`.
    ///
    /// @param lignes les lignes sur lesquelles lire les valeurs offertes ; depuis #3095 c'est le
    ///     « tous sauf lui » de l'écran, jamais sa liste déjà filtrée
    /// @param dimensions les dimensions offertes, **dans l'ordre d'affichage**
    public static <T> CritereFiltre<T> de(Supplier<? extends List<T>> lignes, List<Dimension<T>> dimensions) {
        List<Dimension<T>> offertes = List.copyOf(dimensions);
        return CritereListe.multipleParmi(
                ClesCriteres.LIEU,
                "Lieu",
                "Choisir un lieu",
                () -> groupes(lignes.get(), offertes),
                ligne -> valeursDe(ligne, offertes),
                PAR_SEGMENT);
    }

    /// L'entrée dont **un segment** est exactement la valeur mémorisée, s'il n'y en a qu'une.
    ///
    /// La comparaison porte sur des segments entiers, jamais sur un fragment de texte : « 6403 » ne
    /// désigne pas « 640380 · Vallon ». Une valeur mémorisée est un lieu qui a existé, pas une amorce
    /// de recherche.
    private static Optional<String> parSegment(String memorisee, List<String> entrees) {
        List<String> candidates = entrees.stream()
                .filter(entree -> segments(entree).contains(memorisee))
                .toList();
        return candidates.size() == 1 ? Optional.of(candidates.get(0)) : Optional.empty();
    }

    private static List<String> segments(String entree) {
        return List.of(entree.split(Pattern.quote(SEPARATEUR)));
    }

    /// Les valeurs offertes, un groupe par dimension, dans l'ordre déclaré.
    private static <T> List<CritereListe.GroupeValeurs> groupes(List<T> lignes, List<Dimension<T>> dimensions) {
        return dimensions.stream()
                .map(dimension -> new CritereListe.GroupeValeurs(
                        dimension.titre(), ValeursPresentes.de(lignes, dimension.lecture())))
                .toList();
    }

    /// Ce qu'une ligne vaut sur **toutes** ses dimensions, valeurs absentes écartées : c'est cet
    /// ensemble que la sélection confronte.
    private static <T> List<String> valeursDe(T ligne, List<Dimension<T>> dimensions) {
        return dimensions.stream()
                .map(dimension -> dimension.lecture().apply(ligne))
                .filter(Objects::nonNull)
                .toList();
    }
}
