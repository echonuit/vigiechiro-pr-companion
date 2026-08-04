package fr.univ_amu.iut.commun.view;

import fr.univ_amu.iut.commun.model.LieuQualifie;
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
/// ## Trois niveaux, dont un porte deux étiquettes
///
/// Le domaine n'a que **trois** niveaux géographiques, et non quatre : la **commune** (dérivée du GPS
/// du point, ADR 2791), le **carré** et le **point d'écoute**. Ce qui ressemblait à une quatrième
/// dimension, le « site », est le **nom convivial du carré** : `monitoring_site` porte
/// `square_number` et `friendly_name` sur la même ligne.
///
/// Les deux s'offrent donc dans **une seule entrée** ([#carres]), « 640380 · Vallon », et non dans deux
/// groupes qui retenaient les mêmes lignes (#3157, chantier #3151).
///
/// ## Pourquoi les dimensions restent un paramètre
///
/// Leur composition varie d'un écran à l'autre : Espèces & observations n'offre pas le point tant que
/// sa projection ne remonte pas son code (#3161). C'est un paramètre parce que l'usage en décide, pas
/// une commodité d'implémentation.
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

    private CritereLieu() {}

    /// Où se trouve, dans l'entrée d'**aujourd'hui**, la valeur telle qu'une vue d'**hier** l'a
    /// mémorisée (#3158).
    ///
    /// Qualifier une entrée ne change pas le lieu, seulement son écriture ; encore faut-il savoir **de
    /// quel côté** l'ancienne écriture a survécu. Sans cela, « 640380 » paraît désigner aussi bien le
    /// carré « 640380 · Vallon » que le point « 640380 · A1 », puisque le point est qualifié **par**
    /// son carré. Les deux se disputeraient la valeur, et le rattrapage s'abstiendrait toujours.
    public enum EcritureAncienne {

        /// L'entrée **commence** par elle : « 640380 » a donné « 640380 · Vallon » (#3157).
        EN_TETE,

        /// L'entrée **finit** par elle : « A1 » a donné « 640380 · A1 » (#2992).
        EN_QUEUE;

        /// Vrai si `entree` est l'écriture actuelle de `memorisee`, du côté que désigne cette constante.
        private boolean reconnait(String entree, String memorisee) {
            List<String> segments = segments(entree);
            return memorisee.equals(this == EN_TETE ? segments.getFirst() : segments.getLast());
        }
    }

    /// Une **dimension** géographique offerte par le critère : son intitulé de groupe et ce qu'on lit
    /// sur une ligne.
    ///
    /// @param titre l'en-tête du groupe (« Communes », « Carrés »…), au pluriel comme les autres
    /// @param lecture ce que la dimension vaut pour une ligne, ou `null` si la ligne ne la porte pas
    /// @param ecritureAncienne où retrouver, dans ses valeurs, une valeur mémorisée **avant**
    ///     qualification, ou `null` si cette dimension n'a jamais changé d'écriture (la commune)
    public record Dimension<T>(String titre, Function<T, String> lecture, EcritureAncienne ecritureAncienne) {

        /// Une dimension qui n'a **jamais** changé d'écriture : rien à rattraper.
        public Dimension(String titre, Function<T, String> lecture) {
            this(titre, lecture, null);
        }
    }

    /// La dimension **« Carrés »** : une entrée par carré, portant ses **deux étiquettes** quand la
    /// seconde existe (« 640380 · Vallon »), son numéro seul sinon (#3157).
    ///
    /// Le numéro et le nom convivial ne sont pas deux dimensions : `monitoring_site` porte les deux
    /// colonnes, et cocher « 640380 » puis « Vallon » retenait exactement les mêmes lignes. Les offrir
    /// séparément allongeait le menu d'une entrée par carré sans rien ajouter, et laissait deux carrés
    /// homonymes se confondre sous un même nom - le défaut que #2992 avait corrigé pour les points.
    ///
    /// @param numero le numéro officiel du carré, l'identité de l'entrée
    /// @param nomConvivial le nom que l'utilisateur a donné au site, ou `null` s'il ne l'a pas nommé
    public static <T> Dimension<T> carres(Function<T, String> numero, Function<T, String> nomConvivial) {
        return new Dimension<>(
                "Carrés",
                ligne -> LieuQualifie.qualifier(numero.apply(ligne), nomConvivial.apply(ligne)),
                EcritureAncienne.EN_TETE);
    }

    /// La dimension **« Points »** : un point **qualifié par son carré** (« 640380 · A1 », #2992), le
    /// code seul n'étant unique que dans son carré (`UNIQUE(site_id, code)`).
    ///
    /// @param pointQualifie ce que vaut le point d'une ligne, déjà qualifié, ou `null` si elle n'en
    ///     porte pas. La règle de qualification reste à la feature : son suffixe **est** le lieu, et
    ///     un point sans code ne donne aucune entrée, là où un carré sans nom garde son numéro
    public static <T> Dimension<T> points(Function<T, String> pointQualifie) {
        return new Dimension<>("Points", pointQualifie, EcritureAncienne.EN_QUEUE);
    }

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
                memorisee -> rattraper(memorisee, lignes.get(), offertes));
    }

    /// L'entrée qui est l'**écriture d'aujourd'hui** de `memorisee`, s'il n'y en a qu'une.
    ///
    /// Chaque dimension est interrogée sur **ses propres** valeurs, du côté qu'elle déclare : le carré
    /// reconnaît « 640380 » en tête de « 640380 · Vallon », le point reconnaît « A1 » en queue de
    /// « 640380 · A1 ». C'est ce cloisonnement qui rend la réponse univoque, là où un « un segment
    /// quelque part » trouvait toujours deux prétendants, le point étant qualifié **par** son carré.
    ///
    /// La comparaison porte sur des **segments entiers**, jamais sur un fragment : « 6403 » ne désigne
    /// pas « 640380 · Vallon ». Une valeur mémorisée est un lieu qui a existé, pas une amorce de
    /// recherche.
    ///
    /// **Deux candidates ne donnent rien** : deviner entre deux lieux reviendrait à filtrer sur celui
    /// que l'utilisateur n'a pas choisi. #3093 dit alors ce qui n'a pas été replacé.
    private static <T> Optional<String> rattraper(String memorisee, List<T> lignes, List<Dimension<T>> dimensions) {
        List<String> candidates = dimensions.stream()
                .filter(dimension -> dimension.ecritureAncienne() != null)
                .flatMap(dimension -> ValeursPresentes.de(lignes, dimension.lecture()).stream()
                        .filter(valeur -> dimension.ecritureAncienne().reconnait(valeur, memorisee)))
                .distinct()
                .toList();
        return candidates.size() == 1 ? Optional.of(candidates.getFirst()) : Optional.empty();
    }

    private static List<String> segments(String entree) {
        return List.of(entree.split(Pattern.quote(LieuQualifie.SEPARATEUR)));
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
