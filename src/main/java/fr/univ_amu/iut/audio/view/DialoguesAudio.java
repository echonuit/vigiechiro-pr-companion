package fr.univ_amu.iut.audio.view;

import fr.univ_amu.iut.commun.api.ParticipationVigieChiro;
import fr.univ_amu.iut.commun.view.ChoixDansListe;
import fr.univ_amu.iut.commun.view.ConfirmateurModifiable;
import fr.univ_amu.iut.commun.view.DemandeurDeChoixModifiable;
import fr.univ_amu.iut.commun.view.SelecteurFichierJavaFx;
import fr.univ_amu.iut.commun.view.SelecteurFichierModifiable;
import java.util.Objects;
import java.util.function.Supplier;
import javafx.stage.Window;

/// Les **porteurs de dialogue** de la vue audio, réunis (#1431). Extraits de [SonsValidationController]
/// pour le garder sous son plafond de taille (PMD `NcssCount`) - et parce qu'ils forment bel et bien une
/// unité : ce sont les trois endroits où l'écran **demande quelque chose à l'utilisateur**.
///
/// - le **oui/non** ([ConfirmateurModifiable], #1013) : réimporter par-dessus des validations, publier
///   des corrections - des gestes qui écrasent quelque chose ;
/// - la **désignation** d'un fichier ([SelecteurFichierModifiable], #1431) : importer un CSV, exporter le
///   `_Vu`, les observations, la bibliothèque ;
/// - le **choix** ([DemandeurDeChoixModifiable], #1431) : à quelle participation VigieChiro rattacher
///   cette nuit ?
///
/// Chacun est remplaçable par un double en test. Sans cela, ces gestes s'arrêteraient sur un
/// `showAndWait` natif, qui **fige** un test headless - et l'écran n'aurait, comme avant, que des boutons
/// dont on sait seulement qu'ils existent.
final class DialoguesAudio {

    private final ConfirmateurModifiable confirmateur = new ConfirmateurModifiable();

    private final SelecteurFichierModifiable selecteur;

    private final DemandeurDeChoixModifiable<ParticipationVigieChiro> participation;

    /// @param fenetre fenêtre propriétaire des dialogues, évaluée **au moment de demander** (l'écran peut
    ///     ne pas encore être attaché à une fenêtre quand le contrôleur est construit)
    DialoguesAudio(Supplier<Window> fenetre) {
        Objects.requireNonNull(fenetre, "fenetre");
        this.selecteur = new SelecteurFichierModifiable(new SelecteurFichierJavaFx(fenetre));
        this.participation = new DemandeurDeChoixModifiable<>(new ChoixDansListe<>(fenetre));
    }

    ConfirmateurModifiable confirmateur() {
        return confirmateur;
    }

    SelecteurFichierModifiable selecteur() {
        return selecteur;
    }

    DemandeurDeChoixModifiable<ParticipationVigieChiro> participation() {
        return participation;
    }
}
