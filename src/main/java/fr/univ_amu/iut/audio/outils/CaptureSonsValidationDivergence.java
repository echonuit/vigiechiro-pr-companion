package fr.univ_amu.iut.audio.outils;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.model.Empreintes;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.passage.model.IdentiteSequence;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;

/// Outil de capture/mesure, utilisable tel quel.
///
/// Capture l'aperçu de la vue audio unifiée (« Sons & validation ») dans l'état **identité divergente**
/// (#2254, #2334) : le fichier trouvé au chemin attendu **n'est pas** celui qui a produit les
/// observations, et l'écran remplace donc le lecteur par un **encart d'avertissement** plutôt que de
/// laisser écouter (ou valider) un autre enregistrement en silence.
///
/// Le socle [GraineSonsValidation] seede des séquences avec le constructeur de compatibilité (empreinte
/// et taille `NULL`) : elles ne peuvent pas diverger telles quelles ([IdentiteSequence#divergence]
/// renvoie vide sans empreinte stockée). Cet outil fabrique donc la divergence en deux gestes, pour la
/// séquence sélectionnée comme pour les autres :
///
/// 1. il **pose** sur la séquence l'empreinte (et la taille) du fichier original écrit par la graine,
///    via [SequenceDao#majEmpreinte] (le geste du rétro-remplissage #1299) ;
/// 2. il **substitue** ensuite ce fichier par un WAV **valide mais au contenu différent**, de **même
///    taille** : l'empreinte recalculée en direct diffère de l'empreinte stockée, sans que la
///    comparaison de taille ne tranche avant elle. Le motif de divergence est donc « empreinte de
///    contenu différente », et non « taille ... ».
///
/// La première ligne de la table est ensuite sélectionnée (comme [CaptureSonsValidation]) : sa séquence
/// diverge, l'`AudioView` s'efface et l'encart s'affiche à sa place.
///
/// Lancement headless : `.github/assets/capture-screenshots.sh` (Headless Platform JavaFX 26).
public final class CaptureSonsValidationDivergence {

    private static final int FREQUENCE_ECHANTILLONNAGE = 44_100;
    private static final int OCTETS_ENTETE_WAV = 44;

    private CaptureSonsValidationDivergence() {}

    public static void main(String[] args) throws InterruptedException {
        CountDownLatch fini = new CountDownLatch(1);
        AtomicReference<Throwable> erreur = new AtomicReference<>();
        Platform.startup(() -> {
            try {
                capturer();
            } catch (RuntimeException | IOException probleme) {
                erreur.set(probleme);
            } finally {
                fini.countDown();
            }
        });
        fini.await();
        Platform.exit();
        if (erreur.get() != null) {
            erreur.get().printStackTrace();
            System.exit(1);
        }
        System.exit(0);
    }

    private static void capturer() throws IOException {
        Injector injecteur = GraineSonsValidation.preparer();
        SourceDeDonnees source = injecteur.getInstance(SourceDeDonnees.class);
        substituerLesFichiers(new SequenceDao(source));
        GraineSonsValidation.rendre(
                injecteur,
                GraineSonsValidation.dossierSortie().resolve("apercu-sons-validation-substituee.png"),
                1100,
                vue -> {
                    GraineSonsValidation.selectionner(vue, 0);
                    tracerEncart(vue);
                });
    }

    /// Rend divergente **chaque** séquence encore sur le disque : pose l'empreinte du fichier original,
    /// puis remplace ce fichier par un WAV de même taille au contenu différent. Ainsi, quelle que soit
    /// la séquence portée par la première ligne de la table, elle diverge. Trace le motif effectif de
    /// chaque séquence, pour prouver que la divergence est bien active au moment du rendu.
    private static void substituerLesFichiers(SequenceDao sequenceDao) throws IOException {
        for (SequenceDEcoute sequence : sequenceDao.findAll()) {
            Path chemin = Path.of(sequence.cheminFichier());
            if (!Files.exists(chemin)) {
                continue;
            }
            long taille = Files.size(chemin);
            String empreinteOriginale = Empreintes.empreinteCourte(chemin);
            // (1) l'empreinte STOCKÉE devient celle du fichier original de la graine (#1299).
            sequenceDao.majEmpreinte(sequence.id(), taille, empreinteOriginale);
            // (2) le fichier est remplacé par un autre WAV valide, de MÊME taille, contenu différent.
            ecrireWavDifferent(chemin, taille);

            SequenceDEcoute apres = sequenceDao.findById(sequence.id()).orElseThrow();
            String motif = IdentiteSequence.divergence(apres).orElse("(aucune divergence)");
            System.out.println("[divergence] " + sequence.nomFichier() + " : " + motif);
        }
    }

    /// Écrit à `cible` un WAV PCM 16 bits mono, 44,1 kHz, de **taille identique** à `tailleOctets` (donc
    /// à celle du fichier remplacé), mais au **contenu différent** : une sinusoïde pure de 2 kHz, sans
    /// rapport avec les cris FM déterministes de [fr.univ_amu.iut.commun.outils.SonDemo]. L'en-tête reste
    /// un conteneur WAV correct, décodable par l'`AudioView` ; seul le PCM change, ce qui suffit à faire
    /// diverger l'empreinte courte (SHA-256 des 64 premiers Kio) sans toucher à la taille.
    private static void ecrireWavDifferent(Path cible, long tailleOctets) throws IOException {
        int nbEchantillons = (int) ((tailleOctets - OCTETS_ENTETE_WAV) / 2);
        short[] echantillons = new short[nbEchantillons];
        for (int i = 0; i < nbEchantillons; i++) {
            double tSec = i / (double) FREQUENCE_ECHANTILLONNAGE;
            echantillons[i] = (short) (Math.sin(2 * Math.PI * 2_000 * tSec) * 20_000);
        }
        try (OutputStream sortie = Files.newOutputStream(cible)) {
            ecrireWav(sortie, echantillons, FREQUENCE_ECHANTILLONNAGE);
        }
    }

    /// Sérialise `echantillons` (PCM 16 bits mono) dans un conteneur WAV/RIFF minimal (little-endian),
    /// à l'identique de l'en-tête écrit par la graine, pour que seule change la charge PCM.
    private static void ecrireWav(OutputStream sortie, short[] echantillons, int frequence) throws IOException {
        int tailleData = echantillons.length * 2;
        ByteBuffer buffer = ByteBuffer.allocate(OCTETS_ENTETE_WAV + tailleData).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(36 + tailleData);
        buffer.put("WAVE".getBytes(StandardCharsets.US_ASCII));
        buffer.put("fmt ".getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(16); // taille du sous-bloc fmt
        buffer.putShort((short) 1); // PCM
        buffer.putShort((short) 1); // mono
        buffer.putInt(frequence);
        buffer.putInt(frequence * 2); // débit octets/s
        buffer.putShort((short) 2); // alignement de bloc
        buffer.putShort((short) 16); // bits par échantillon
        buffer.put("data".getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(tailleData);
        for (short echantillon : echantillons) {
            buffer.putShort(echantillon);
        }
        sortie.write(buffer.array());
    }

    /// Trace, au moment du rendu, ce que montre réellement le panneau d'écoute : le texte de l'encart de
    /// divergence et sa visibilité. Preuve que le grisage est bien à l'écran, et pas seulement en base.
    private static void tracerEncart(Parent vue) {
        if (vue.lookup("#encartAudioDivergent") instanceof Node encart) {
            System.out.println("[encart] encartAudioDivergent visible = " + encart.isVisible());
        }
        if (vue.lookup("#lblMotifDivergence") instanceof Label motif) {
            System.out.println("[encart] lblMotifDivergence = " + motif.getText());
        }
    }

    /// Injecteur (partiel) utilisé par cet outil de capture. Exposé pour le garde-fou de câblage (test).
    public static Injector creerInjecteur() {
        return GraineSonsValidation.creerInjecteur();
    }
}
