package fr.univ_amu.iut.importation.model;

import fr.univ_amu.iut.commun.persistence.ArborescenceFichiers;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/// Remplacement **atomique** de la session physique lors d'un écrasement (#214).
///
/// Le dossier de session est **déterministe** (dérivé du quadruplet point/année/n°) : l'ancien et le
/// nouvel import visent le **même** dossier `bruts/`. Réimporter sans précaution laisserait donc des
/// WAV fantômes de l'ancien import contaminer le renommage, et détruirait l'ancien avant que le nouveau
/// soit acquis. Pour un remplacement sûr, on met l'ancienne session **de côté** (renommage en
/// `<nom>.remplace`), on réimporte dans un dossier **propre**, puis :
///
/// - succès → l'ancienne mise de côté est supprimée (le nouveau passage l'a remplacée en base) ;
/// - échec (annulation, tous les WAV rejetés, erreur disque…) → l'ancienne est **restaurée** : rien
///   n'est perdu.
///
/// La suppression **en base** de l'ancien passage est, elle, faite dans la **même transaction** que
/// l'insertion du nouveau (cf. [ServiceImport]) : un échec laisse donc l'ancien passage intact, cohérent
/// avec sa session physique restaurée.
final class RemplacementSession {

    private static final Logger LOG = Logger.getLogger(RemplacementSession.class.getName());

    private RemplacementSession() {}

    /// Exécute `reimport` en protégeant l'ancienne session `dossierSession` : mise de côté avant, puis
    /// suppression physique (succès) ou restauration (échec). Renvoie le résultat de `reimport` ; relaie
    /// telle quelle l'exception d'un import avorté, après avoir restauré l'ancienne session.
    static ResultatImport autourDe(Path dossierSession, Supplier<ResultatImport> reimport) {
        Path miseDeCote = mettreDeCote(dossierSession);
        try {
            ResultatImport resultat = reimport.get();
            if (miseDeCote != null) {
                ArborescenceFichiers.effacerAuMieux(miseDeCote); // ancien physique désormais obsolète
            }
            return resultat;
        } catch (RuntimeException echec) {
            restaurer(miseDeCote, dossierSession);
            throw echec;
        }
    }

    /// Renomme l'ancienne session en `<nom>.remplace` pour repartir d'un dossier propre. Renvoie le
    /// chemin de mise à l'écart, ou `null` s'il n'y avait rien à déplacer (premier import à ce quadruplet).
    private static Path mettreDeCote(Path dossierSession) {
        if (!Files.isDirectory(dossierSession)) {
            return null;
        }
        Path miseDeCote = dossierSession.resolveSibling(dossierSession.getFileName() + ".remplace");
        ArborescenceFichiers.effacerAuMieux(miseDeCote); // reliquat d'un écrasement précédent interrompu
        try {
            Files.move(dossierSession, miseDeCote);
            return miseDeCote;
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Préparation de l'écrasement impossible (mise de côté de la session) : " + dossierSession, e);
        }
    }

    /// Supprime le nouvel import partiel et remet l'ancienne session en place. **Best-effort** : ne masque
    /// pas l'échec d'origine (annulation, rejets…) ; si la restauration elle-même échoue, l'ancienne reste
    /// récupérable sous `.remplace` et la ligne en base de l'ancien passage est intacte (suppression
    /// différée, dans une transaction non atteinte).
    private static void restaurer(Path miseDeCote, Path dossierSession) {
        if (miseDeCote == null) {
            return;
        }
        ArborescenceFichiers.effacerAuMieux(dossierSession); // nouvel import partiel
        try {
            Files.move(miseDeCote, dossierSession);
        } catch (IOException restaurationImpossible) {
            // On laisse l'ancienne session sous « .remplace » plutôt que de masquer l'échec d'origine.
            LOG.log(
                    Level.WARNING,
                    restaurationImpossible,
                    () -> "Ancienne session laissée sous « .remplace » : " + miseDeCote);
        }
    }
}
