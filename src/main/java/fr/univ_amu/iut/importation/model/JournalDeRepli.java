package fr.univ_amu.iut.importation.model;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

/// Reconstitue une **identité de repli** (#107) quand aucun journal LogPR n'est présent : un
/// [JournalParse] minimal porté par les **noms des WAV** (`PaRecPR<série>_<date>_…`, même motif que
/// [AnalyseMelange]). Permet d'importer en **mode dégradé** sans bloquer — l'absence de journal est par
/// ailleurs signalée à l'inspection (avertissement non bloquant).
///
/// Pour ne pas **coincer l'aval** (la préparation du lot exige une trace de journal, cf.
/// `VerificationCoherence`), le mode dégradé écrit une **trace synthétique assumée** ([#ecrireTraceSynthetique])
/// et le journal de repli porte une **anomalie explicite** ([#ANOMALIE_ABSENCE]) : la nuit reste
/// déposable, et sa nature dégradée est tracée (visible au diagnostic).
///
/// Extrait de [ServiceImport] pour le garder cohésif (la reconstitution d'identité est une
/// préoccupation autonome).
final class JournalDeRepli {

    /// Série inscrite si aucun nom de WAV n'est exploitable (la colonne `recorder.serial_number` est
    /// `NOT NULL`). Cas rare : les noms bruts portent presque toujours la série.
    static final String SERIE_INCONNUE = "PR-INCONNU";

    /// Anomalie inscrite au journal synthétique pour assumer et tracer le mode dégradé (#107).
    static final String ANOMALIE_ABSENCE =
            "Journal LogPR absent : import en mode dégradé (enregistreur et date déduits des noms de fichiers).";

    /// Nom du fichier de **trace synthétique** écrit dans la session quand le journal est absent.
    private static final String NOM_TRACE = "JOURNAL-ABSENT.txt";

    private static final String CONTENU_TRACE = "Journal du capteur (LogPR<n>.txt) absent sur la carte SD.\n"
            + "Cette nuit a été importée en mode dégradé (#107) : l'enregistreur et la date ont été\n"
            + "déduits des noms de fichiers WAV. Les paramètres d'acquisition ne sont pas disponibles.\n";

    private JournalDeRepli() {}

    /// Journal de repli : série + date extraites des noms d'`originaux` (la **première** série et la
    /// **première** date triées — choix déterministe ; un dossier mélangé reste signalé, non bloquant, à
    /// l'inspection). Les autres champs sont nuls, ce que la construction du passage et du micro tolère
    /// déjà ; une **anomalie** [#ANOMALIE_ABSENCE] est portée pour assumer le mode dégradé.
    static JournalParse depuis(List<Path> originaux) {
        AnalyseMelange analyse = AnalyseMelange.depuis(originaux);
        String serie =
                analyse.series().isEmpty() ? SERIE_INCONNUE : analyse.series().first();
        LocalDate date = analyse.nuits().isEmpty() ? null : analyse.nuits().first();
        return new JournalParse(
                serie,
                null,
                date,
                null,
                null,
                null,
                null,
                null,
                false,
                null,
                List.of(),
                List.of(new LigneJournal(null, ANOMALIE_ABSENCE)));
    }

    /// Écrit la **trace synthétique** de journal dégradé dans `dossierSession` et renvoie son chemin :
    /// `sensor_log.file_path` étant `NOT NULL` et la préparation du lot exigeant un journal, le mode
    /// dégradé dépose une trace assumée plutôt que de laisser un trou bloquant.
    static Path ecrireTraceSynthetique(Path dossierSession) {
        Path cible = dossierSession.resolve(NOM_TRACE);
        try {
            Files.createDirectories(dossierSession);
            Files.writeString(cible, CONTENU_TRACE, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Écriture de la trace de journal dégradé impossible : " + cible, e);
        }
        return cible;
    }
}
