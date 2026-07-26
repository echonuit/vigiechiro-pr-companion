package fr.univ_amu.iut.passage.viewmodel;

import fr.univ_amu.iut.passage.model.DetailPassage;
import fr.univ_amu.iut.passage.model.DisponibiliteAudio;

/// Règles **pures** d'activation de la réactivation d'un passage (#1302), avec le **motif** de son
/// blocage. Extraites de [PassageViewModel] pour qu'il garde la seule responsabilité de porter l'état
/// observable de l'écran (PMD GodClass).
///
/// Le gating est **en amont** (#789) : on grise avec une explication plutôt que de laisser
/// l'utilisateur découvrir le refus après avoir confirmé.
final class GatingReactivation {

    private GatingReactivation() {}

    /// Ce que la règle a besoin de savoir **en plus du détail** : d'où l'audio pourrait revenir.
    ///
    /// Un objet-paramètre plutôt que deux booléens en file (#2483) : à deux arguments de même type,
    /// une inversion ne se voit ni à la lecture ni à la compilation.
    ///
    /// @param rattacheeAUneParticipation la nuit est liée à une participation Vigie-Chiro, donc il existe
    ///     une source d'où récupérer la liste de ses fichiers
    /// @param hydratationDisponible la passerelle qui les récupère est là (connecté, et fonctionnalité
    ///     « Import Vigie-Chiro » active)
    record ContexteReactivation(boolean rattacheeAUneParticipation, boolean hydratationDisponible) {

        /// La nuit peut-elle recevoir ses séquences depuis la plateforme ?
        boolean hydratationPossible() {
            return rattacheeAUneParticipation && hydratationDisponible;
        }
    }

    /// Réactiver a du sens dans deux situations, qu'il ne faut pas confondre.
    ///
    /// **Il manque de l'audio à une nuit qui a ses séquences** : le cas d'origine (#1302), audio absent
    /// (`ABSENTE`) ou disque incomplet (`PARTIELLE`).
    ///
    /// **La nuit n'a aucune séquence, mais la plateforme sait lesquelles elle devrait avoir** : une nuit
    /// rapatriée par la synchro en **squelette** (ADR 0016). La réactivation rapatrie alors ses
    /// observations avant de rebrancher l'audio (#2555). Griser ici revenait à refuser le seul geste qui
    /// rendait la nuit exploitable, en annonçant qu'il n'y avait « rien à réactiver ».
    static boolean reactivationPossible(DetailPassage detail, ContexteReactivation contexte) {
        if (detail.decompteAudio().total() == 0) {
            return contexte.hydratationPossible();
        }
        return detail.decompteAudio().disponibilite() != DisponibiliteAudio.COMPLETE;
    }

    /// Motif du blocage de la réactivation ; chaîne vide quand elle est possible.
    ///
    /// Un motif dit la **cause** et le **geste**, pas seulement l'absence : « ce passage n'a aucune
    /// séquence importée localement » décrivait un état sans dire ni pourquoi ni quoi faire, sur des nuits
    /// qui n'attendaient qu'une connexion.
    static String motifReactivation(DetailPassage detail, ContexteReactivation contexte) {
        if (detail.decompteAudio().total() == 0) {
            return motifNuitSansSequence(contexte);
        }
        if (detail.decompteAudio().disponibilite() == DisponibiliteAudio.COMPLETE) {
            return "Rien à réactiver : l'audio de ce passage est déjà sur le disque.";
        }
        return "";
    }

    /// Pourquoi une nuit **sans aucune séquence** ne peut pas être réactivée. Depuis #2555 elle le peut
    /// dans le cas courant : ne restent bloquées que celles dont on ne saurait pas de quoi elles sont
    /// faites, et chacune a son geste.
    private static String motifNuitSansSequence(ContexteReactivation contexte) {
        if (contexte.hydratationPossible()) {
            return "";
        }
        if (!contexte.rattacheeAUneParticipation()) {
            return "Rien à réactiver : ce passage n'a aucune séquence en local, et n'est rattaché à aucune"
                    + " participation Vigie-Chiro d'où récupérer la liste de ses fichiers.";
        }
        return "Cette nuit a été rapatriée de Vigie-Chiro, mais ses observations n'ont pas encore été"
                + " récupérées : connectez-vous à Vigie-Chiro (menu ☰) pour pouvoir la réactiver.";
    }
}
