package fr.univ_amu.iut.commun.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/// À quoi l'on compare un nombre de contacts (#2351) : la saison, la région et le milieu.
///
/// Les trois ne se connaissent pas de la même façon, et c'est **volontaire** :
///
/// - la **saison** se déduit de la date du passage : elle est dans la donnée ;
/// - la **région** se déduit du numéro de carré, dont les deux premiers chiffres sont le département
///   ([RegionDuCarre]) ;
/// - le **milieu** ne se devine pas. Aucune donnée du produit ne dit si un point est en forêt ou en
///   ville. Il reste un **choix explicite**, dont la valeur par défaut est l'absence de choix : donc
///   le national, et non une supposition.
///
/// @param saison saison retenue, ou vide (on comparera alors « toutes saisons »)
/// @param region région du carré, ou vide (national)
/// @param milieu milieu choisi par l'utilisateur, ou vide (national)
public record ContexteActivite(Optional<SaisonActivite> saison, Optional<String> region, Optional<String> milieu) {

    /// Contexte le plus large : aucune déclinaison, toutes saisons. C'est le **défaut**, et il est
    /// juste : simplement moins précis.
    public static final ContexteActivite NATIONAL =
            new ContexteActivite(Optional.empty(), Optional.empty(), Optional.empty());

    public ContexteActivite {
        saison = saison == null ? Optional.empty() : saison;
        region = region == null ? Optional.empty() : region;
        milieu = milieu == null ? Optional.empty() : milieu;
    }

    /// Le contexte d'un passage : sa saison depuis la date, sa région depuis le carré, et le milieu que
    /// l'utilisateur a choisi (ou pas).
    public static ContexteActivite de(LocalDate nuit, String numeroCarre, String milieuChoisi) {
        return new ContexteActivite(
                Optional.ofNullable(nuit).flatMap(SaisonActivite::de),
                RegionDuCarre.pour(numeroCarre),
                Optional.ofNullable(milieuChoisi).filter(m -> !m.isBlank()));
    }

    /// Les déclinaisons à essayer, **du plus précis au plus général**. Le milieu passe avant la région :
    /// c'est un choix délibéré de l'utilisateur, là où la région est déduite.
    public List<String> declinaisonsParPrecision() {
        List<String> ordre = new ArrayList<>();
        milieu.ifPresent(m -> ordre.add("habitat:" + m));
        region.ifPresent(r -> ordre.add("region:" + r));
        ordre.add("national");
        return List.copyOf(ordre);
    }

    /// Les saisons à essayer : la saison précise d'abord, `toutes` ensuite.
    public List<String> saisonsParPrecision() {
        List<String> ordre = new ArrayList<>();
        saison.ifPresent(s -> ordre.add(s.cle()));
        ordre.add("toutes");
        return List.copyOf(ordre);
    }

    /// Ce à quoi la comparaison a été faite, en clair, pour le dire à l'écran et le recopier à l'export.
    /// L'utilisateur doit savoir **à quoi** son nombre a été comparé, sans quoi la classe est un oracle.
    ///
    /// Les noms passent par [LibellesReferentiel] : les clés du référentiel n'ont ni accents ni
    /// apostrophes, et se lisaient jusqu'ici telles quelles (« region Provence-Alpes-Cote dAzur »,
    /// #3049). La **clé** reste celle qui joint la donnée ; seule cette phrase change.
    public String libelle() {
        StringBuilder texte = new StringBuilder();
        texte.append(milieu.map(m -> "milieu " + LibellesReferentiel.milieu(m))
                .orElseGet(() -> region.map(r -> "région " + LibellesReferentiel.region(r))
                        .orElse("national")));
        saison.ifPresent(s -> texte.append(" · ").append(s.libelle()));
        return texte.toString();
    }
}
