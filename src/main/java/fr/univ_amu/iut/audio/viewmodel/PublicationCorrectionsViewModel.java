package fr.univ_amu.iut.audio.viewmodel;

import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.validation.model.BilanPublication;
import fr.univ_amu.iut.validation.model.PublicationCorrections;
import fr.univ_amu.iut.validation.model.TriPublication;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;

/// ViewModel de la **publication des corrections vers VigieChiro** (#723), distinct de
/// [AudioViewModel] (concern à part, VM déjà volumineux) et jumeau de [ImportVigieChiroViewModel] :
/// coordonne le service [PublicationCorrections] et l'état IHM (en cours / message de restitution).
///
/// La publication est **optionnelle** : `publication` est vide dans les injecteurs partiels de
/// capture (assemblés sans `connexion`) et quand la feature `publier-corrections` est coupée. VM
/// agnostique de l'IHM (règle ArchUnit `viewmodel_sans_javafx_ui`).
public class PublicationCorrectionsViewModel {

    private final Optional<PublicationCorrections> publication;

    /// Publication en cours (posée pendant le travail hors fil JavaFX) : l'IHM désactive l'action.
    private final ReadOnlyBooleanWrapper enCours = new ReadOnlyBooleanWrapper(this, "enCours", false);

    /// Message de restitution de la dernière publication (résumé, écarts, refus, ou erreur), pour l'IHM.
    private final ReadOnlyStringWrapper message = new ReadOnlyStringWrapper(this, "message", "");

    public PublicationCorrectionsViewModel(Optional<PublicationCorrections> publication) {
        this.publication = Objects.requireNonNull(publication, "publication");
    }

    /// `true` si la publication est **disponible** dans ce contexte (application connectée, feature
    /// active) : l'IHM masque l'action sinon.
    public boolean disponible() {
        return publication.isPresent();
    }

    /// Trie les observations revues du passage (aperçu de la confirmation : rien n'est envoyé).
    /// **Bloquant** (base) : à appeler hors du fil JavaFX. Lève si la publication est indisponible ou
    /// si rien n'est revu.
    public TriPublication trier(Long idPassage) {
        return moteur().trier(idPassage);
    }

    /// Publie les corrections du passage. **Bloquant** (réseau) : à appeler **hors du fil JavaFX**. Ne
    /// mute aucun état observable ; l'appelant applique le résultat au fil JavaFX ([#appliquerBilan] /
    /// [#echec]).
    public BilanPublication publier(Long idPassage) {
        return moteur().publier(idPassage);
    }

    /// Variante **suivie et annulable** (#1838) : acquiert d'abord l'**ancrage manquant** (rapatriement
    /// des `donnees`, page par page) puis publie. Une nuit déjà ancrée n'en paie pas le coût et la
    /// progression reste muette. **Bloquant** : à appeler hors du fil JavaFX.
    public BilanPublication publier(Long idPassage, Consumer<Progression> progres, JetonAnnulation jeton) {
        return moteur().publier(idPassage, progres, jeton);
    }

    /// L'ancrage manquant de ce passage sera-t-il **acquis** par la publication (#1838) ? Lecture
    /// **locale** (les liens sont en base), donc appelable depuis l'aperçu de la confirmation : c'est
    /// elle qui distingue « ces corrections seront d'abord ancrées » de « rien à publier ».
    public boolean ancrageAcquerable(Long idPassage) {
        return moteur().ancrageAcquerable(idPassage);
    }

    /// Signale le **début** de la publication (au fil JavaFX, avant le travail en arrière-plan).
    public void marquerEnCours() {
        message.set("Publication des corrections vers Vigie-Chiro…");
        enCours.set(true);
    }

    /// Restitue une publication **terminée** (au fil JavaFX) : résumé du bilan, écarts et refus compris.
    public void appliquerBilan(BilanPublication bilan) {
        enCours.set(false);
        message.set(resume(bilan));
    }

    /// Restitue un **échec** ou une **annulation** (au fil JavaFX) : message d'erreur métier / réseau,
    /// ou chaîne vide pour effacer l'état « en cours » après une annulation.
    public void echec(String erreur) {
        enCours.set(false);
        message.set(erreur);
    }

    /// Résumé lisible d'un bilan : envoyées, écartées par cause, refus (avec la première cause en
    /// exemple : le détail complet vit dans le bilan, la CLI l'imprime intégralement).
    static String resume(BilanPublication bilan) {
        List<String> clauses = new ArrayList<>();
        clauses.add(String.format("%d envoyée(s)", bilan.poussees()));
        siNonNul(clauses, bilan.sansCertitude(), "%d à compléter (certitude non déclarée)");
        // Depuis #1838 la publication ancre elle-même ce qui peut l'être : ce qui reste ici n'est pas un
        // oubli de réimport, c'est une nuit sans participation à quoi s'ancrer. Le remède a changé.
        siNonNul(
                clauses,
                bilan.sansAncrage(),
                "%d sans ancrage plateforme (rattachez la nuit à sa participation Vigie-Chiro)");
        siNonNul(clauses, bilan.horsReferentiel(), "%d hors référentiel");

        StringBuilder resume =
                new StringBuilder("Corrections publiées vers Vigie-Chiro : ").append(String.join(", ", clauses));
        if (!bilan.sansEchec()) {
            // ⚠️ Une seule cause sur N est montrée : ce résumé est un compte rendu tronqué en phrase, et
            // c'est un frontalier au sens de l'ADR 0031 (le bilan porte la liste, la phrase n'en dit qu'un).
            // Traité par le sous-EPIC #2004, pas ici : ce lot ne change que la façon d'assembler.
            resume.append(String.format(
                    " ; %d refus, dont : %s",
                    bilan.echecs().size(), bilan.echecs().getFirst()));
        }
        resume.append('.');
        if (!bilan.rapatriement().estMuet()) {
            // Le rapatriement d'ancrage ramène aussi les échanges avec le validateur (#1867). Les taire
            // reviendrait à laisser l'observateur les découvrir en ouvrant la bonne observation, par
            // hasard. Le texte vient du port d'import, qui seul sait ce qu'il a écrit.
            resume.append(' ').append(bilan.rapatriement().texte());
        }
        return resume.toString();
    }

    /// Ajoute une clause **si elle a lieu d'être** : annoncer « 0 hors référentiel » serait du bruit.
    ///
    /// La condition est nommée une fois au lieu d'être recopiée, et le `%d` dit que la clause est bornée -
    /// deux choses qu'une chaîne d'`append` taisait.
    private static void siNonNul(List<String> clauses, int combien, String gabarit) {
        if (combien > 0) {
            clauses.add(String.format(gabarit, combien));
        }
    }

    private PublicationCorrections moteur() {
        return publication.orElseThrow(
                () -> new RegleMetierException("Publication des corrections indisponible dans ce contexte."));
    }

    public ReadOnlyBooleanProperty enCoursProperty() {
        return enCours.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty messageProperty() {
        return message.getReadOnlyProperty();
    }

    /// Message de la confirmation : ce qui va partir, ce qui sera d'abord ancré, ce qui restera à quai,
    /// et le caractère définitif (une correction publiée se remplace côté plateforme, elle ne se retire
    /// pas).
    ///
    /// Vit ici, et non dans la vue : composer une phrase à partir d'un [TriPublication] est de la
    /// **présentation**, comme [#resume] l'est pour le bilan. La vue s'est contentée de la montrer.
    ///
    /// **Public** pour la même raison que [fr.univ_amu.iut.commun.view.ConfirmationNavigation#dialogue]
    /// (#1468) : la capture de documentation doit obtenir **ce texte-ci**, celui que l'utilisateur
    /// lira, et non une copie « à l'identique » qui n'engage personne. Le dépôt a déjà vu des dialogues
    /// documentés dériver du produit, jusqu'à une confirmation entière qui manquait (#1865).
    ///
    /// @param ancrageAVenir l'envoi va lui-même acquérir l'ancrage manquant (#1838) : les observations
    ///     sans ancrage ne « restent pas à quai », elles seront ancrées avant de partir
    public static String recapitulatif(TriPublication tri, boolean ancrageAVenir) {
        boolean ancrage = ancrageAVenir && tri.sansAncrage() > 0;
        StringBuilder message = new StringBuilder();
        if (ancrage) {
            // On se garde d'annoncer un total : une observation à ancrer peut aussi manquer de certitude,
            // et ne partirait donc pas non plus. Promettre un compte que l'envoi démentirait serait pire
            // que de ne rien promettre.
            message.append("Publier les corrections de ce passage vers Vigie-Chiro ?")
                    .append("\n\n")
                    .append(tri.publiables().size())
                    .append(" prête(s) à partir, et ")
                    .append(tri.sansAncrage())
                    .append(" à ancrer d'abord : leur ancrage sera rapatrié depuis Vigie-Chiro (vos"
                            + " validations sont préservées), ce qui peut prendre quelques minutes. Seules"
                            + " celles dont la certitude est déclarée partiront ensuite.");
        } else {
            message.append("Publier ")
                    .append(tri.publiables().size())
                    .append(" correction(s) de ce passage vers Vigie-Chiro ?");
        }
        String quai = ecarts(tri, !ancrage);
        if (!quai.isEmpty()) {
            message.append("\n\nResteront à quai : ").append(quai).append('.');
        }
        message.append("\n\nLes valeurs déjà publiées seront réécrites ; une correction publiée ne peut"
                + " pas être retirée de la plateforme.");
        return message.toString();
    }

    /// Détail des écartées, par cause (seules les causes présentes sont citées). `inclureSansAncrage`
    /// est faux quand l'envoi va justement acquérir cet ancrage : ces observations ne restent pas à quai.
    public static String ecarts(TriPublication tri, boolean inclureSansAncrage) {
        StringBuilder ecarts = new StringBuilder();
        if (tri.sansCertitude() > 0) {
            ecarts.append(tri.sansCertitude()).append(" sans certitude déclarée");
        }
        if (inclureSansAncrage && tri.sansAncrage() > 0) {
            separer(ecarts);
            ecarts.append(tri.sansAncrage()).append(" sans ancrage plateforme");
        }
        if (tri.horsReferentiel() > 0) {
            separer(ecarts);
            ecarts.append(tri.horsReferentiel()).append(" hors référentiel");
        }
        return ecarts.toString();
    }

    private static void separer(StringBuilder ecarts) {
        if (!ecarts.isEmpty()) {
            ecarts.append(", ");
        }
    }
}
