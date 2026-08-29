package fr.univ_amu.iut.passage.model;

import fr.univ_amu.iut.commun.api.MeteoDepot;
import java.util.Objects;

/// Le sort de la **météo** dans un envoi, quand trois valeurs sont en présence (#4757) : la **base**
/// (ce que la plateforme portait à notre dernière lecture), la **nôtre** (ce que le passage local dit)
/// et la **leur** (ce que la plateforme porte maintenant).
///
/// Comparer la base à la leur suffit à voir qu'ils ont écrit, et c'est ce que faisait la garde de
/// #4707 ; cela ne suffit pas à savoir s'il y a **désaccord**. Un collègue qui saisit la météo pendant
/// que nous ne la touchons pas ne nous contredit en rien : il n'y a rien à arbitrer, et refuser l'envoi
/// entier bloquait alors la configuration matérielle, qui n'était pour rien dans l'affaire.
///
/// La sortie n'est pas « laquelle des trois envoyer » mais **envoyer la nôtre, ou taire le champ** :
/// une clé absente du corps laisse la plateforme garder la sienne, [fr.univ_amu.iut.commun.api.RequetesVigieChiro]
/// ne sérialisant pas les `null`. C'est ce qui rend le partage possible sans que personne n'arbitre.
///
/// | base | nous | eux | ce qui se passe |
/// |---|---|---|---|
/// | M0 | M0 | M1 | on tait le champ, leur saisie survit |
/// | M0 | M1 | M0 | on envoie la nôtre |
/// | M0 | M1 | M1 | on tait le champ, il porte déjà notre valeur |
/// | M0 | M1 | M2 | **conflit** : les deux côtés ont écrit, et pas la même chose |
///
/// @param conflit vrai si les deux côtés ont modifié la météo, différemment
/// @param aEnvoyer la météo à mettre dans le corps, ou `null` pour **taire** le champ
record ResolutionMeteo(boolean conflit, MeteoDepot aEnvoyer) {

    /// Ce que devient la météo entre ces trois valeurs. `null` et un bloc entièrement vide y désignent
    /// la même chose - une météo non renseignée -, sans quoi une saisie effacée d'un côté et jamais
    /// remplie de l'autre passerait pour un désaccord.
    static ResolutionMeteo entre(MeteoDepot base, MeteoDepot notre, MeteoDepot leur) {
        if (memeMeteo(notre, base)) {
            // Nous n'y avons pas touché : nous n'avons rien à dire sur ce champ, et le taire est la
            // seule façon de ne pas écraser ce qu'ils y ont mis. Le cas nominal - personne n'a rien
            // changé - passe par ici aussi, et taire un champ inchangé ne coûte rien.
            return new ResolutionMeteo(false, null);
        }
        if (memeMeteo(leur, base) || memeMeteo(notre, leur)) {
            // Soit ils n'ont pas bougé, soit ils ont abouti à notre valeur : dans les deux cas notre
            // saisie ne contredit personne.
            return new ResolutionMeteo(false, notre);
        }
        return new ResolutionMeteo(true, null);
    }

    /// Deux météos disent-elles la même chose ? Un bloc dont les quatre composants sont absents ne dit
    /// rien de plus que pas de bloc du tout : la plateforme rend l'un ou l'autre selon qu'une météo a
    /// été saisie puis vidée, et nous n'avons pas à en faire un désaccord.
    private static boolean memeMeteo(MeteoDepot une, MeteoDepot autre) {
        return Objects.equals(sansVide(une), sansVide(autre));
    }

    /// La météo, ou `null` si elle ne porte aucune valeur.
    private static MeteoDepot sansVide(MeteoDepot meteo) {
        if (meteo == null
                || (meteo.vent() == null
                        && meteo.couverture() == null
                        && meteo.temperatureDebut() == null
                        && meteo.temperatureFin() == null)) {
            return null;
        }
        return meteo;
    }
}
