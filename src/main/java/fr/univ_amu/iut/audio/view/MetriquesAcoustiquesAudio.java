package fr.univ_amu.iut.audio.view;

import fr.nedjar.vigiechiro.audio.AudioView;
import fr.univ_amu.iut.audio.viewmodel.ComparateursAudio;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

/// Grandeurs d'identification acoustiques (#500) : **FME** (fréquence du max d'énergie) et **fréquence
/// terminale**, bien plus discriminantes que la fréquence médiane, mais **absentes du CSV Tadarida
/// simplifié**. L'audio-view les calcule sur le signal du cri **sélectionné** (fenêtre surlignée).
///
/// **Stratégie paresseuse + cache.** L'analyse n'existe que pour l'audio chargé (l'observation courante).
/// On **mémorise** la mesure par observation au fil de la navigation ; les colonnes se **remplissent
/// progressivement** (« — » tant que le cri n'a pas été sélectionné). Évite d'analyser ~2000 fichiers à
/// l'import. Logique sortie du controller (pur câblage) pour tenir les seuils de cohésion PMD.
final class MetriquesAcoustiquesAudio {

    /// Mesures d'un cri, en **Hz réels** (ou `NaN` si l'audio-view n'a pas pu déterminer la grandeur).
    private record Mesures(double fmeHz, double frequenceTerminaleHz) {}

    private final Map<Long, Mesures> parObservation = new HashMap<>();

    /// Installe **toute** la fonctionnalité FME / fréquence terminale sur les deux colonnes : leur
    /// `cellValueFactory` (lue d'un cache privé), leur comparateur numérique, et la **capture** — à chaque
    /// recalcul des grandeurs par l'audio-view (la propriété FME change au chargement d'un clip et à chaque
    /// surlignage), on mémorise la mesure pour l'observation **courante** et on rafraîchit la table pour
    /// peupler ses cellules. Le cache vit dans les fermetures (aucun état à porter côté controller).
    static void installer(
            AudioView audioView,
            ObservableValue<LigneObservationAudio> selection,
            TableView<LigneObservationAudio> table,
            TableColumn<LigneObservationAudio, String> colFme,
            TableColumn<LigneObservationAudio, String> colFreqTerminale) {
        MetriquesAcoustiquesAudio cache = new MetriquesAcoustiquesAudio();
        colFme.setCellValueFactory(
                c -> new ReadOnlyStringWrapper(cache.fmeColonne(c.getValue().idObservation())));
        colFreqTerminale.setCellValueFactory(c -> new ReadOnlyStringWrapper(
                cache.frequenceTerminaleColonne(c.getValue().idObservation())));
        colFme.setComparator(ComparateursAudio.comparateurNumerique());
        colFreqTerminale.setComparator(ComparateursAudio.comparateurNumerique());
        // FME et fréquence terminale ne se stabilisent pas forcément au même instant : on capte sur les
        // DEUX propriétés (et on relit les deux à chaque fois) pour ne rater aucune grandeur tardive.
        Runnable capter = () -> {
            LigneObservationAudio observation = selection.getValue();
            // Pas d'observation (séquence non identifiée) : rien à mémoriser (le cache est indexé par
            // observation). L'écoute reste possible, mais les grandeurs Tadarida ne s'y rattachent pas.
            if (observation != null
                    && observation.idObservation() != null
                    && cache.memoriser(
                            observation.idObservation(), audioView.getFmeHz(), audioView.getFrequenceTerminaleHz())) {
                table.refresh(); // peuple les cellules FME / fréquence terminale de la ligne
            }
        };
        audioView.fmeHzProperty().addListener((obs, avant, apres) -> capter.run());
        audioView.frequenceTerminaleHzProperty().addListener((obs, avant, apres) -> capter.run());
    }

    /// Mémorise les grandeurs (en Hz) d'une observation, en **fusionnant** avec ce qui est déjà connu : une
    /// grandeur `NaN` (indéterminée à cet instant) ne **remplace pas** une valeur finie déjà captée. Renvoie
    /// `true` seulement si le cache a **changé** (→ inutile de rafraîchir sinon). Rien n'est mémorisé si les
    /// deux grandeurs restent indéterminées.
    boolean memoriser(long idObservation, double fmeHz, double frequenceTerminaleHz) {
        Mesures avant = parObservation.get(idObservation);
        double fme = Double.isNaN(fmeHz) && avant != null ? avant.fmeHz() : fmeHz;
        double terminale = Double.isNaN(frequenceTerminaleHz) && avant != null
                ? avant.frequenceTerminaleHz()
                : frequenceTerminaleHz;
        if (Double.isNaN(fme) && Double.isNaN(terminale)) {
            return false;
        }
        Mesures apres = new Mesures(fme, terminale);
        if (apres.equals(avant)) {
            return false; // rien de nouveau
        }
        parObservation.put(idObservation, apres);
        return true;
    }

    /// FME de l'observation formatée en **kHz** (« 52 kHz »), ou « — » si pas encore calculée / indéterminée,
    /// ou si la ligne n'a pas d'observation (`idObservation` nul : séquence non identifiée).
    String fmeColonne(Long idObservation) {
        Mesures mesures = parObservation.get(idObservation);
        return mesures == null ? "—" : kiloHertz(mesures.fmeHz());
    }

    /// Fréquence terminale de l'observation formatée en **kHz**, ou « — » si pas encore calculée / absente.
    String frequenceTerminaleColonne(Long idObservation) {
        Mesures mesures = parObservation.get(idObservation);
        return mesures == null ? "—" : kiloHertz(mesures.frequenceTerminaleHz());
    }

    private static String kiloHertz(double hz) {
        return Double.isNaN(hz) ? "—" : String.format(Locale.ROOT, "%d kHz", Math.round(hz / 1000));
    }
}
