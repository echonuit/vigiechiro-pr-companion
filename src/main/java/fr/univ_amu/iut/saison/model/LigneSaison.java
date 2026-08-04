package fr.univ_amu.iut.saison.model;

import java.util.List;
import java.util.stream.Stream;

/// Une ligne du solde de saison : un **point suivi**, l'état de ses deux passages attendus, et la
/// phrase d'action **« reste à faire »**. C'est cette dernière colonne qui transforme un tableau de
/// suivi en plan de travail : un état se décrit, une action se fait (#2356).
///
/// Le carré et le code du point donnent l'identité affichée ; `idPoint` sert au double-clic (ouvrir
/// le point quand il n'a pas encore de passage).
///
/// @param numeroCarre numéro du carré (site) auquel appartient le point
/// @param codePoint code du point d'écoute dans le carré
/// @param idPoint identifiant technique du point (pour l'ouverture)
/// Les deux colonnes de passage ne montrent que le **protocole** : une nuit opportuniste (#2525),
/// réalisée sur le carré d'un tiers, n'y figure pas. Elle occuperait la place du passage protocolaire
/// qui, lui, **manque** : une case remplie et un « reste à faire » qui dit d'aller poser l'enregistreur
/// se contredisent sur la même ligne. Ces nuits ont donc leur propre colonne, [#horsProtocole].
///
/// @param passage1 état du premier passage **protocolaire** (présent ou absent)
/// @param passage2 état du second passage **protocolaire** (présent ou absent)
/// @param horsProtocole nuits réalisées sur ce point mais hors protocole (opportunistes), dans l'ordre
///     des numéros de passage ; vide dans le cas courant
/// @param nomSite nom **convivial** du carre, ou `null` s il n en a pas : la seconde etiquette du
///     meme lieu (ADR 3157), par laquelle il se cherche aussi (#3215)
/// @param resteAFaire phrase de l'action à mener sur ce point, ou chaîne **vide** si le point est à
///     jour
public record LigneSaison(
        String numeroCarre,
        String codePoint,
        Long idPoint,
        CasePassage passage1,
        CasePassage passage2,
        List<CasePassage> horsProtocole,
        String resteAFaire,
        String nomSite) {

    public LigneSaison {
        horsProtocole = List.copyOf(horsProtocole);
    }

    /// Vrai si le point est **à jour** : plus aucune action à mener (les deux passages sont terminés).
    public boolean aJour() {
        return resteAFaire.isEmpty();
    }

    /// Toutes les cases de la ligne, protocolaires et hors protocole : ce sur quoi porte un filtre qui
    /// interroge les nuits du point (la campagne, par exemple).
    public Stream<CasePassage> toutesLesCases() {
        return Stream.concat(Stream.of(passage1, passage2), horsProtocole.stream());
    }
}
