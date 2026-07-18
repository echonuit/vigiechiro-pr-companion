package fr.univ_amu.iut.audio.viewmodel;

import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.validation.model.BilanPublication;
import fr.univ_amu.iut.validation.model.PublicationCorrections;
import fr.univ_amu.iut.validation.model.TriPublication;
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
        message.set("Publication des corrections vers VigieChiro…");
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
        StringBuilder resume = new StringBuilder("Corrections publiées vers VigieChiro : ")
                .append(bilan.poussees())
                .append(" envoyée(s)");
        if (bilan.sansCertitude() > 0) {
            resume.append(", ").append(bilan.sansCertitude()).append(" à compléter (certitude non déclarée)");
        }
        if (bilan.sansAncrage() > 0) {
            // Depuis #1838 la publication ancre elle-même ce qui peut l'être : ce qui reste ici n'est pas
            // un oubli de réimport, c'est une nuit sans participation à quoi s'ancrer. Le remède a changé.
            resume.append(", ")
                    .append(bilan.sansAncrage())
                    .append(" sans ancrage plateforme (rattachez la nuit à sa participation VigieChiro)");
        }
        if (bilan.horsReferentiel() > 0) {
            resume.append(", ").append(bilan.horsReferentiel()).append(" hors référentiel");
        }
        if (!bilan.sansEchec()) {
            resume.append(" ; ")
                    .append(bilan.echecs().size())
                    .append(" refus, dont : ")
                    .append(bilan.echecs().getFirst());
        }
        return resume.append('.').toString();
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
}
