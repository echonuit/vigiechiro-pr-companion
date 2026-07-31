package fr.univ_amu.iut.audit.model;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import fr.univ_amu.iut.lot.model.DepotUnite;
import fr.univ_amu.iut.lot.model.TypeDepotUnite;
import fr.univ_amu.iut.lot.model.dao.DepotUniteDao;
import fr.univ_amu.iut.passage.model.EnregistrementOriginal;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import fr.univ_amu.iut.passage.model.SessionDEnregistrement;
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Bilan de récupérabilité (#1151) : ce que deviendrait l'audio de chaque nuit si l'on repartait d'une base
/// neuve.
///
/// Les trois branches de la cascade sont **les trois seuls verdicts possibles**, et l'issue exige qu'ils
/// soient établis **avant** d'écrire quoi que ce soit. Le cas qui compte est le dernier : une nuit déposée
/// en **ZIP** (le mode par défaut) dont le disque n'a plus les fichiers est **perdue** : le serveur ne les
/// a jamais eus.
class ServiceRecuperabiliteTest {

    private static final String ID_USER = "u-1";
    private static final String SERIE = "1925492";

    @TempDir
    Path dossier;

    private SourceDeDonnees source;
    private Workspace workspace;
    private PassageDao passageDao;
    private SessionDao sessionDao;
    private SequenceDao sequenceDao;
    private EnregistrementOriginalDao originalDao;
    private DepotUniteDao depotDao;
    private LienVigieChiroDao liens;
    private ServiceRecuperabilite service;

    @BeforeEach
    void preparer() {
        workspace = new Workspace(dossier);
        source = new SourceDeDonnees(workspace);
        new MigrationSchema(source).migrer();
        // La topologie naît du premier `creerNuit`, par trouver-ou-créer.
        passageDao = new PassageDao(source);
        sessionDao = new SessionDao(source);
        sequenceDao = new SequenceDao(source);
        originalDao = new EnregistrementOriginalDao(source);
        depotDao = new DepotUniteDao(source);
        liens = new LienVigieChiroDao(source);
        service = new ServiceRecuperabilite(source, workspace);
    }

    @Test
    @DisplayName("Séquences sur le disque : l'audio revient du DISQUE (le cas normal, et le seul rapide)")
    void audio_sur_le_disque() throws IOException {
        Long idPassage = creerNuit(1, true);

        BilanRecuperabilite bilan = service.bilan();

        assertThat(bilan.nuits()).singleElement().satisfies(nuit -> {
            assertThat(nuit.idPassage()).isEqualTo(idPassage);
            assertThat(nuit.source()).isEqualTo(SourceAudio.DISQUE);
            assertThat(nuit.sequencesPresentes()).isEqualTo(2);
            assertThat(nuit.libelle())
                    .as("l'utilisateur qui s'apprête à tout perdre ne raisonne pas en identifiants")
                    .contains("130711")
                    .contains("Z41");
        });
        assertThat(bilan.perteAnnoncee()).isFalse();
    }

    @Test
    @DisplayName("Disque vide + dépôt WAV rattaché : le SERVEUR peut rendre l'audio")
    void audio_depuis_le_serveur() throws IOException {
        Long idPassage = creerNuit(1, false);
        depotDao.insert(DepotUnite.aDeposer(idPassage, "seq-1.wav", TypeDepotUnite.WAV, "2026-07-01"));
        liens.upsert(new LienVigieChiro(LienVigieChiro.ENTITE_PASSAGE, String.valueOf(idPassage), "6a53f5fa"));

        BilanRecuperabilite bilan = service.bilan();

        assertThat(bilan.nuits()).singleElement().satisfies(nuit -> {
            assertThat(nuit.source()).isEqualTo(SourceAudio.SERVEUR);
            assertThat(nuit.motif()).contains("WAV");
        });
        assertThat(bilan.perteAnnoncee())
                .as("rien n'est perdu : le serveur a gardé les fichiers d'un dépôt WAV")
                .isFalse();
    }

    @Test
    @DisplayName("Disque vide + dépôt ZIP : PERDU, le serveur ne garde aucun audio d'une archive")
    void audio_perdu_car_depot_zip() throws IOException {
        Long idPassage = creerNuit(1, false);
        depotDao.insert(DepotUnite.aDeposer(idPassage, "lot.zip", TypeDepotUnite.ZIP, "2026-07-01"));
        liens.upsert(new LienVigieChiro(LienVigieChiro.ENTITE_PASSAGE, String.valueOf(idPassage), "6a53f5fa"));

        BilanRecuperabilite bilan = service.bilan();

        assertThat(bilan.nuits()).singleElement().satisfies(nuit -> {
            assertThat(nuit.source())
                    .as("c'est LE cas par défaut depuis #984, et le plus dangereux : la nuit est rattachée,"
                            + " tout semble en ordre, et pourtant l'audio n'existe nulle part")
                    .isEqualTo(SourceAudio.PERDU);
            assertThat(nuit.motif()).contains("ZIP");
        });
        assertThat(bilan.perteAnnoncee()).isTrue();
        assertThat(bilan.perdues()).hasSize(1);
    }

    @Test
    @DisplayName("Disque vide + jamais déposée : PERDU (le serveur n'en a aucune copie)")
    void audio_perdu_car_jamais_deposee() throws IOException {
        creerNuit(1, false);

        BilanRecuperabilite bilan = service.bilan();

        assertThat(bilan.nuits()).singleElement().satisfies(nuit -> {
            assertThat(nuit.source()).isEqualTo(SourceAudio.PERDU);
            assertThat(nuit.motif()).contains("jamais déposée");
        });
    }

    @Test
    @DisplayName("Le résumé compte les trois sources, et la perte n'est annoncée que s'il y en a une")
    void resume_des_trois_sources() throws IOException {
        creerNuit(1, true); // disque
        Long deposeeZip = creerNuit(2, false); // perdue
        depotDao.insert(DepotUnite.aDeposer(deposeeZip, "lot.zip", TypeDepotUnite.ZIP, "2026-07-01"));
        liens.upsert(new LienVigieChiro(LienVigieChiro.ENTITE_PASSAGE, String.valueOf(deposeeZip), "6a53f5fb"));

        BilanRecuperabilite bilan = service.bilan();

        assertThat(bilan.nombre(SourceAudio.DISQUE)).isEqualTo(1);
        assertThat(bilan.nombre(SourceAudio.PERDU)).isEqualTo(1);
        assertThat(bilan.resume()).contains("2 nuit(s)").contains("1 perdue(s)");
        assertThat(bilan.perteAnnoncee())
                .as("une seule nuit perdue suffit à exiger une confirmation")
                .isTrue();
    }

    // --- Fixture -----------------------------------------------------------------------------------

    /// Crée une nuit avec 2 séquences déclarées en base ; `surDisque` décide si leurs fichiers existent
    /// réellement. C'est toute la différence entre « je réimporte » et « c'est perdu ».
    private Long creerNuit(int numeroPassage, boolean surDisque) throws IOException {
        Long idPassage = JeuDeDonneesPassage.dans(source)
                .utilisateur(ID_USER)
                .carre("130711")
                .nomSite("Carré")
                .point("Z41")
                .enregistreur(SERIE)
                .nuit(numeroPassage, 2026, "2026-07-0" + numeroPassage)
                .statut(StatutWorkflow.DEPOSE)
                .deposeLe("2026-07-0" + numeroPassage)
                .semerPassage()
                .idPassage();
        String nomSession = "Car130711-2026-Pass" + numeroPassage + "-Z41";
        Path racine = workspace.dossierSession(nomSession);
        Path transformes = workspace.dossierTransformes(nomSession);
        Long idSession = sessionDao
                .insert(new SessionDEnregistrement(null, racine.toString(), 0L, 0L, idPassage))
                .id();
        Long idOriginal = originalDao
                .insert(new EnregistrementOriginal(
                        null, nomSession + "-brut.wav", racine.toString(), null, null, null, idSession))
                .id();
        if (surDisque) {
            Files.createDirectories(transformes);
        }
        for (int index = 0; index < 2; index++) {
            String nom = nomSession + "-seq" + index + ".wav";
            Path fichier = transformes.resolve(nom);
            if (surDisque) {
                Files.writeString(fichier, "audio");
            }
            sequenceDao.insert(new SequenceDEcoute(
                    null, nom, idOriginal, index, null, null, fichier.toString(), false, idSession, null));
        }
        return idPassage;
    }
}
