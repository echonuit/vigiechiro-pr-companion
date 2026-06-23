package fr.univ_amu.iut.importation.model;

import fr.univ_amu.iut.commun.model.Prefixe;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/// Inspecte un dossier de carte SD **en lecture seule** (parcours P2, première étape). Il ne
/// **jamais rien écrire** : conformément à R9, la source reste intacte ; la copie protégée vient
/// ensuite, séparément ([CopieProtegee]).
///
/// Trois informations sont collectées :
///
/// - le **journal du capteur** `LogPR<n>.txt`, parsé via [AnalyseurLogPR] (n° de série, paramètres
/// d'acquisition, anomalies) ;
/// - les **enregistrements originaux** WAV présents (à la racine, ou dans un sous-dossier `bruts/`
/// si la source est déjà organisée comme une session R22) ;
/// - le **relevé climatique** `*_THLog.csv`, éventuellement absent (R20) ;
/// - l'**état de nommage** ([EtatNommage]) : fichiers encore bruts (R7) ou déjà préfixés (R6).
public class InspecteurDossier {

    private static final String SOUS_DOSSIER_BRUTS = "bruts";

    private final AnalyseurLogPR analyseurLog;

    public InspecteurDossier(AnalyseurLogPR analyseurLog) {
        this.analyseurLog = Objects.requireNonNull(analyseurLog, "analyseurLog");
    }

    /// Inspecte `dossierSource` sans le modifier.
    ///
    /// @throws IllegalArgumentException si le chemin n'existe pas ou n'est pas un dossier
    public RapportInspection inspecter(Path dossierSource) {
        Objects.requireNonNull(dossierSource, "dossierSource");
        if (!Files.isDirectory(dossierSource)) {
            throw new IllegalArgumentException("Dossier source introuvable : " + dossierSource);
        }
        Path cheminJournal = trouverPremier(dossierSource, this::estJournalLog);
        JournalParse journal = cheminJournal == null ? null : analyseurLog.analyser(cheminJournal);
        Path releve = trouverPremier(dossierSource, this::estReleveClimatique);
        List<Path> originaux = listerOriginaux(dossierSource);
        EtatNommage etat = determinerEtatNommage(originaux);
        return new RapportInspection(dossierSource, cheminJournal, journal, releve, originaux, etat);
    }

    /// Enregistrements originaux : dans `bruts/` si présent, sinon à la racine du dossier.
    private List<Path> listerOriginaux(Path dossierSource) {
        Path bruts = dossierSource.resolve(SOUS_DOSSIER_BRUTS);
        Path ou = Files.isDirectory(bruts) ? bruts : dossierSource;
        try (Stream<Path> flux = Files.list(ou)) {
            return flux.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".wav"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Lecture du dossier impossible : " + ou, e);
        }
    }

    /// Détermine l'état de nommage des originaux : aucun fichier [EtatNommage#VIDE] ; tous préfixés
    /// `Car...` [EtatNommage#PREFIXE] ; au moins un fichier brut [EtatNommage#BRUT].
    private EtatNommage determinerEtatNommage(List<Path> originaux) {
        if (originaux.isEmpty()) {
            return EtatNommage.VIDE;
        }
        boolean tousPrefixes = originaux.stream()
                .allMatch(p -> Prefixe.estNomPrefixe(p.getFileName().toString()));
        return tousPrefixes ? EtatNommage.PREFIXE : EtatNommage.BRUT;
    }

    private boolean estJournalLog(String nom) {
        return nom.matches("(?i)LogPR\\d+\\.txt");
    }

    private boolean estReleveClimatique(String nom) {
        return nom.toUpperCase().contains("THLOG") && nom.toLowerCase().endsWith(".csv");
    }

    private Path trouverPremier(Path dossier, java.util.function.Predicate<String> nomAccepte) {
        try (Stream<Path> flux = Files.list(dossier)) {
            return flux.filter(Files::isRegularFile)
                    .filter(p -> nomAccepte.test(p.getFileName().toString()))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            throw new UncheckedIOException("Lecture du dossier impossible : " + dossier, e);
        }
    }
}
