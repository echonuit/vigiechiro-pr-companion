package fr.univ_amu.iut.audio.viewmodel;

import fr.univ_amu.iut.commun.viewmodel.Formats;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.MessageObservation;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/// Formatages du **troisième avis** : ce que le validateur du MNHN a tranché, et la discussion qui
/// l'entoure (#1417).
///
/// Classe à part, et pas trois méthodes de plus dans [FormatLigneAudio] : celui-ci formate *la ligne*
/// (ses temps, ses fréquences, son statut de revue), tandis qu'on formate ici *un avis d'expert* et *un
/// fil de messages* — deux notions que la ligne ne portait pas hier. Les y ajouter faisait basculer
/// `FormatLigneAudio` en God Class au sens de PMD, ce qui n'était pas un caprice de l'outil : les
/// responsabilités avaient bien divergé.
public final class FormatAvisValidateur {

    /// Format de date d'un message du fil (« 11/07/2026 21:04 »), en heure locale : le serveur date en UTC.
    private static final DateTimeFormatter QUAND = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRENCH);

    private FormatAvisValidateur() {}

    /// Libellé de la colonne **« Avis du validateur »** : le nom vernaculaire du taxon tranché par
    /// l'expert s'il est connu, sinon son code (souche hors référentiel), suivi de sa certitude — et le
    /// tiret « — » tant qu'aucun expert ne s'est prononcé, ce qui reste le cas le plus courant.
    ///
    /// La certitude est **accolée au taxon** plutôt que mise en colonne séparée : un avis se lit d'un bloc
    /// (« Pipistrelle commune · Sûr »), et la table compte déjà dix-huit colonnes.
    public static String avis(LigneObservationAudio o) {
        if (!o.trancheeParUnValidateur()) {
            return Formats.VALEUR_ABSENTE;
        }
        String taxon =
                o.nomValidateur() != null && !o.nomValidateur().isBlank() ? o.nomValidateur() : o.taxonValidateur();
        return o.certitudeValidateur() == null
                ? taxon
                : taxon + " · " + o.certitudeValidateur().libelle();
    }

    /// Classe CSS de l'avis : **mise en avant du désaccord**. Un expert qui confirme ne demande rien ; un
    /// expert qui **contredit** l'observateur est ce qu'il faut voir en premier — c'est là que se joue la
    /// qualité de la donnée déposée.
    public static String classeBadge(LigneObservationAudio o) {
        if (!o.trancheeParUnValidateur()) {
            return "badge-validateur-absent";
        }
        return o.validateurEnDesaccord() ? "badge-validateur-desaccord" : "badge-validateur-accord";
    }

    /// Colonne du **fil de discussion** : le nombre de messages échangés. Vide (et non « 0 ») quand
    /// personne n'a écrit — une colonne d'indicateurs doit rester silencieuse tant qu'il n'y a rien à
    /// signaler ; l'en-tête porte le pictogramme, comme « ⭐ » et « 💬 » à côté.
    public static String marqueFil(LigneObservationAudio o) {
        return o.aUnFil() ? String.valueOf(o.nbMessages()) : "";
    }

    /// **Auteur** d'un message, tel qu'il s'affiche dans le fil. Le serveur ne donne qu'un objectid : on le
    /// compare à celui de notre propre profil pour dire « Vous », et on s'en tient à « Le validateur »
    /// sinon — plutôt qu'un appel réseau par auteur, pour un nom dont le fil n'a pas besoin.
    ///
    /// Un auteur inconnu (le serveur ne l'a pas renseigné) n'est **pas** attribué : mieux vaut un message
    /// anonyme qu'un message faussement signé.
    public static String auteur(MessageObservation message, String idProfilConnecte) {
        if (message.auteur() == null) {
            return "Auteur inconnu";
        }
        return message.deMoi(idProfilConnecte) ? "Vous" : "Le validateur";
    }

    /// **Date** d'un message, en heure locale. Le serveur ne date pas toujours : un fil daté à moitié reste
    /// un fil lisible, on n'invente pas une date pour combler le trou.
    public static String quand(MessageObservation message) {
        return message.date() == null ? "" : QUAND.format(message.date().atZone(ZoneId.systemDefault()));
    }
}
