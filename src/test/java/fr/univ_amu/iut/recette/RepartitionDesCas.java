package fr.univ_amu.iut.recette;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/// Range chaque cas de recette dans l'un des trois bacs, et signale les désaccords (#3764).
///
/// ## Pourquoi ce calcul ne vit pas dans le test qui l'utilise
///
/// Il y vivait, et il y était **invérifiable**. [CorrespondanceRecetteTest] alimente son décompte en
/// balayant le classpath : on ne peut lui présenter aucun jeu d'annotations fabriqué, donc rien ne
/// prouvait qu'il rangerait un cas perceptif du bon côté. Un garde dont on ne peut pas voir le
/// verdict basculer n'est pas un garde. Sorti ici, le tri reçoit des ensembles ordinaires, et
/// [RepartitionDesCasTest] peut lui montrer les situations que le dépôt ne contient pas encore.
///
/// ## Qui décide de quoi
///
/// **Le script décide du bac.** La marque `*perceptif*` qualifie le cas, et elle se pose en passe 6,
/// quand on constate qu'aucune assertion ne le tranchera - donc **avant** qu'un test existe. Le
/// code, lui, dit seulement ce qu'il prétend prouver ([Jugement]).
///
/// Les deux parlent du même sujet, ce qui permet de les **confronter** :
///
/// | Le script | Le code | Bac | Désaccord |
/// |---|---|---|---|
/// | rien | `AUTOMATIQUE` | asserté | non |
/// | rien | rien | non couvert | non |
/// | `*perceptif*` | `HUMAIN` | perceptif | non |
/// | `*perceptif*` | rien | perceptif | non, et c'est l'état du jour de `S1-26` |
/// | `*perceptif*` | `AUTOMATIQUE` | perceptif | **oui** |
/// | rien | `HUMAIN` | non couvert | **oui** |
///
/// Un cas marqué perceptif ne rejoint **jamais** les assertés, quoi qu'en dise le code. La
/// contradiction se signale, elle ne se résout pas en silence dans le sens qui arrange le compteur :
/// c'est exactement le vert creux que ce dispositif combat.
///
/// ## Ce qui reste ouvert
///
/// `HUMAIN` dit « le verdict repose sur un humain », et non « ce scénario apparaît dans le film ».
/// La distinction ne coûte rien tant qu'un cas asserté n'est filmé que par son propre test
/// d'assertion, ce qui est le cas aujourd'hui. Si l'index par cas amène un jour des scénarios de
/// parcours qui traversent des cas déjà assertés, c'est ici qu'il faudra trancher, avec l'exemple
/// sous les yeux plutôt qu'en le devinant.
///
/// @param assertes les cas qu'une assertion tranche, et elle rougit quand le logiciel a tort
/// @param perceptifs les cas que le script réserve au regard d'un humain
/// @param nonCouverts les cas que rien ne tranche
/// @param desaccords les cas où le script et le code ne disent pas la même chose du juge, associés
///     à ce qu'en dit le **code** (le script dit l'autre)
public record RepartitionDesCas(
        SortedSet<String> assertes,
        SortedSet<String> perceptifs,
        SortedSet<String> nonCouverts,
        SortedMap<String, Jugement> desaccords) {

    public RepartitionDesCas {
        assertes = Collections.unmodifiableSortedSet(new TreeSet<>(assertes));
        perceptifs = Collections.unmodifiableSortedSet(new TreeSet<>(perceptifs));
        nonCouverts = Collections.unmodifiableSortedSet(new TreeSet<>(nonCouverts));
        desaccords = Collections.unmodifiableSortedMap(new TreeMap<>(desaccords));
    }

    /// Répartit les cas déclarés par les scripts de session.
    ///
    /// Seuls les cas **déclarés** sont rangés : un identifiant cité par un test sans exister nulle
    /// part n'appartient à aucun bac, et c'est le premier devoir de [CorrespondanceRecetteTest] de
    /// le refuser. Le confondre avec un cas couvert gonflerait le compteur d'une citation fautive.
    ///
    /// @param declares tous les identifiants que les scripts déclarent
    /// @param perceptifsDuScript ceux que les scripts marquent `*perceptif*`
    /// @param jugementsCites pour chaque identifiant cité, ce que les tests qui le citent
    ///     prétendent prouver
    /// @return les trois bacs, et les désaccords entre les deux sources
    public static RepartitionDesCas repartir(
            Set<String> declares, Set<String> perceptifsDuScript, Map<String, Set<Jugement>> jugementsCites) {

        SortedSet<String> assertes = new TreeSet<>();
        SortedSet<String> perceptifs = new TreeSet<>();
        SortedSet<String> nonCouverts = new TreeSet<>();
        SortedMap<String, Jugement> desaccords = new TreeMap<>();

        for (String cas : new TreeSet<>(declares)) {
            Set<Jugement> jugements = jugementsCites.getOrDefault(cas, Set.of());
            boolean asserte = jugements.contains(Jugement.AUTOMATIQUE);
            boolean confieALOeil = jugements.contains(Jugement.HUMAIN);

            if (perceptifsDuScript.contains(cas)) {
                perceptifs.add(cas);
                if (asserte) {
                    desaccords.put(cas, Jugement.AUTOMATIQUE);
                }
            } else {
                if (asserte) {
                    assertes.add(cas);
                } else {
                    nonCouverts.add(cas);
                }
                if (confieALOeil) {
                    desaccords.put(cas, Jugement.HUMAIN);
                }
            }
        }
        return new RepartitionDesCas(assertes, perceptifs, nonCouverts, desaccords);
    }
}
