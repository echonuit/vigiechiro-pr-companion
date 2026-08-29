package fr.univ_amu.iut.qualification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.api.ProfilVigieChiro;
import fr.univ_amu.iut.commun.model.MethodeSelection;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.VerdictFichier;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.persistence.UniteDeTravail;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import fr.univ_amu.iut.passage.model.EnregistrementOriginal;
import fr.univ_amu.iut.passage.model.ManifestePaquet;
import fr.univ_amu.iut.passage.model.OuvertureDePaquet;
import fr.univ_amu.iut.passage.model.PaquetOuvert;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import fr.univ_amu.iut.passage.model.SessionDEnregistrement;
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.qualification.model.SelectionDEcoute;
import fr.univ_amu.iut.qualification.model.SequenceSelectionnee;
import fr.univ_amu.iut.qualification.model.ServiceEmport;
import fr.univ_amu.iut.qualification.model.dao.SelectionDao;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Le parcours d'emport, joué de bout en bout par un service et non par des DAO (#4726).
///
/// Les pièces existaient toutes depuis #4705 sans qu'aucun appelant de production ne les relie.
/// C'est ce chaînon qui manque, et sans lui l'écran n'a rien à appeler.
class ServiceEmportTest {

    private static final String ID_USER = "u-1";
    private static final ProfilVigieChiro RELECTEUR =
            new ProfilVigieChiro("507f1f77bcf86cd799439011", "chiro-pierre", "Observateur");

    @TempDir
    Path dossier;

    private SourceDeDonnees source;
    private SelectionDao selectionDao;
    private SequenceDao sequenceDao;
    private ServiceEmport emport;
    private long idPassage;
    private long idSession;
    private long idOriginal;

    @BeforeEach
    void preparer() {
        source = new SourceDeDonnees(new Workspace(dossier.resolve("poste-a")));
        new MigrationSchema(source).migrer();
        emport = serviceSur(source);

        idPassage = JeuDeDonneesPassage.dans(source)
                .utilisateur(ID_USER)
                .carre("040962")
                .nomSite("Étang")
                .point("A1")
                .position(43.5, 5.4)
                .enregistreur("1925492")
                .nuit(1, 2026, "2026-06-20")
                .heures("20:00:00", "06:00:00")
                .statut(StatutWorkflow.TRANSFORME)
                .semerPassage()
                .idPassage();
        idSession = new SessionDao(source)
                .insert(new SessionDEnregistrement(null, "/ws/sess", null, null, idPassage))
                .id();
        idOriginal = new EnregistrementOriginalDao(source)
                .insert(new EnregistrementOriginal(null, "orig.wav", "/ws/orig.wav", 5.0, 384000, null, idSession))
                .id();
    }

    @Test
    @DisplayName("Composer emporte les séquences de la sélection et leurs verdicts, et rien d'autre")
    void composer_emporte_la_selection_et_ses_verdicts() throws IOException {
        List<SequenceDEcoute> nuit = creerNuit(5);
        SelectionDEcoute selection =
                selectionDao.insert(new SelectionDEcoute(null, MethodeSelection.REPARTITION_TEMPORELLE, 2, idPassage));
        selectionDao.attacherSequence(
                new SequenceSelectionnee(selection.id(), nuit.get(1).id(), 0, false));
        selectionDao.attacherSequence(
                new SequenceSelectionnee(selection.id(), nuit.get(3).id(), 1, false));
        selectionDao.marquerVerdict(selection.id(), nuit.get(1).id(), VerdictFichier.BON);

        Path paquet = dossier.resolve("nuit.zip");
        long octets = emport.composer(idPassage, paquet);

        assertThat(octets).as("le service rend ce qu'il a écrit").isEqualTo(Files.size(paquet));

        PaquetOuvert ouvert = OuvertureDePaquet.ouvrir(paquet, Optional.of(RELECTEUR));
        assertThat(ouvert.sequences())
                .as("les deux séquences de la sélection, pas les trois autres")
                .containsExactly(
                        "sequences/" + nuit.get(1).nomFichier(),
                        "sequences/" + nuit.get(3).nomFichier());

        ManifestePaquet manifeste = ManifestePaquet.depuis(ouvert.manifeste());
        assertThat(manifeste.carre())
                .as("le préfixe est lu en base, pas deviné d'un nom")
                .isEqualTo("040962");
        assertThat(manifeste.point()).isEqualTo("A1");
        assertThat(manifeste.annee()).isEqualTo(2026);
        assertThat(manifeste.nuit()).isEqualTo(1);
        assertThat(manifeste.sequences())
                .extracting(ManifestePaquet.SequenceEmportee::verdict)
                .as("le verdict posé voyage, celui qui manque voyage comme « non jugé »")
                .containsExactly(VerdictFichier.BON, VerdictFichier.NON_JUGE);
    }

    @Test
    @DisplayName("Reprendre crée une sélection reçue, et la régénération y est ensuite refusée")
    void reprendre_cree_une_selection_recue_et_ferme_la_regeneration() throws IOException {
        Path paquet = unPaquetDeDeuxSequences();
        SourceDeDonnees posteB = posteQuiConnaitLaMemeCampagne();
        ServiceEmport chezB = serviceSur(posteB);
        SelectionDao selectionB = new SelectionDao(posteB);
        Long passageB = passageDe(posteB);

        ServiceEmport.BilanReprise bilan = chezB.reprendre(paquet, Optional.of(RELECTEUR));

        assertThat(bilan.sequences()).as("le bilan dit ce qui a été repris").isEqualTo(2);
        assertThat(bilan.pseudoRelecteur())
                .as("et qui l'a repris, relevé à l'ouverture")
                .isEqualTo("chiro-pierre");
        assertThat(bilan.idPassage()).as("sur la nuit du poste destinataire").isEqualTo(passageB);

        SelectionDEcoute recue = selectionB.findByPassage(passageB).orElseThrow();
        assertThat(recue.methode())
                .as("la sélection n'a pas été tirée ici : elle est arrivée figée")
                .isEqualTo(MethodeSelection.RECUE_D_UN_PAQUET);
        assertThat(selectionB.listerSequences(recue.id()))
                .as("les deux séquences et le verdict de l'expéditeur")
                .hasSize(2)
                .extracting(SequenceSelectionnee::verdict)
                .containsExactly(VerdictFichier.BON, VerdictFichier.NON_JUGE);
    }

    @Test
    @DisplayName("Un paquet dont la nuit est inconnue du poste est refusé, et rien n'est écrit")
    void un_paquet_dont_la_nuit_est_inconnue_est_refuse() throws IOException {
        Path paquet = unPaquetDeDeuxSequences();
        SourceDeDonnees posteVierge = new SourceDeDonnees(new Workspace(dossier.resolve("poste-vierge")));
        new MigrationSchema(posteVierge).migrer();
        ServiceEmport chezVierge = serviceSur(posteVierge);

        assertThatThrownBy(() -> chezVierge.reprendre(paquet, Optional.of(RELECTEUR)))
                .as("le régime de la copie signée suppose deux postes qui connaissent la même campagne")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("040962");

        assertThat(new SelectionDao(posteVierge).findByPassage(1L))
                .as("un refus qui aurait déjà écrit ne serait pas un refus")
                .isEmpty();
    }

    @Test
    @DisplayName("Un paquet dont le point est inconnu du carré est refusé, en nommant le point")
    void un_paquet_dont_le_point_est_inconnu_est_refuse() throws IOException {
        Path paquet = unPaquetDeDeuxSequences();
        SourceDeDonnees posteC = new SourceDeDonnees(new Workspace(dossier.resolve("poste-c")));
        new MigrationSchema(posteC).migrer();
        JeuDeDonneesPassage.dans(posteC)
                .utilisateur(ID_USER)
                .carre("040962")
                .nomSite("Étang")
                .point("B2")
                .position(43.5, 5.4)
                .enregistreur("1925492")
                .nuit(1, 2026, "2026-06-20")
                .heures("20:00:00", "06:00:00")
                .statut(StatutWorkflow.TRANSFORME)
                .semerPassage();

        assertThatThrownBy(() -> serviceSur(posteC).reprendre(paquet, Optional.of(RELECTEUR)))
                .as("le carré est le bon, le point ne l'est pas : le refus doit dire lequel")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("A1");
    }

    @Test
    @DisplayName("Composer une nuit sans sélection est refusé plutôt que de rendre un paquet vide")
    void composer_une_nuit_sans_selection_est_refuse() {
        assertThatThrownBy(() -> emport.composer(idPassage, dossier.resolve("vide.zip")))
                .as("un paquet sans séquence se relirait comme une nuit sans rien à écouter")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sélection");
    }

    @Test
    @DisplayName("Un poste qui connaît le point mais pas cette nuit-là refuse, en nommant la nuit")
    void un_poste_qui_ignore_cette_nuit_refuse() throws IOException {
        Path paquet = unPaquetDeDeuxSequences();
        SourceDeDonnees posteD = new SourceDeDonnees(new Workspace(dossier.resolve("poste-d")));
        new MigrationSchema(posteD).migrer();
        JeuDeDonneesPassage.dans(posteD)
                .utilisateur(ID_USER)
                .carre("040962")
                .nomSite("Étang")
                .point("A1")
                .position(43.5, 5.4)
                .enregistreur("1925492")
                .nuit(7, 2026, "2026-07-10")
                .heures("20:00:00", "06:00:00")
                .statut(StatutWorkflow.TRANSFORME)
                .semerPassage();

        assertThatThrownBy(() -> serviceSur(posteD).reprendre(paquet, Optional.of(RELECTEUR)))
                .as("le bon carré et le bon point, mais la nuit 7 au lieu de la nuit 1")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nuit 1");
    }

    @Test
    @DisplayName("Une nuit connue mais sans session refuse, plutôt que de rattacher à rien")
    void une_nuit_sans_session_refuse() throws IOException {
        Path paquet = unPaquetDeDeuxSequences();
        SourceDeDonnees posteE = new SourceDeDonnees(new Workspace(dossier.resolve("poste-e")));
        new MigrationSchema(posteE).migrer();
        JeuDeDonneesPassage.dans(posteE)
                .utilisateur(ID_USER)
                .carre("040962")
                .nomSite("Étang")
                .point("A1")
                .position(43.5, 5.4)
                .enregistreur("1925492")
                .nuit(1, 2026, "2026-06-20")
                .heures("20:00:00", "06:00:00")
                .statut(StatutWorkflow.TRANSFORME)
                .semerPassage();

        assertThatThrownBy(() -> serviceSur(posteE).reprendre(paquet, Optional.of(RELECTEUR)))
                .as("la ligne de passage existe, la nuit n'a jamais été transférée : ce n'est pas la connaître")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("session");
    }

    @Test
    @DisplayName("Préparer pèse sans écrire, et écrire suit le plan préparé plutôt que d'en refaire un")
    void preparer_pese_sans_ecrire_puis_ecrire_suit_le_plan() throws IOException {
        List<SequenceDEcoute> nuit = creerNuit(3);
        SelectionDEcoute selection =
                selectionDao.insert(new SelectionDEcoute(null, MethodeSelection.MANUEL, 2, idPassage));
        selectionDao.attacherSequence(
                new SequenceSelectionnee(selection.id(), nuit.get(0).id(), 0, false));
        selectionDao.attacherSequence(
                new SequenceSelectionnee(selection.id(), nuit.get(1).id(), 1, false));

        Path paquet = dossier.resolve("prepare.zip");
        ServiceEmport.EmportPrepare prepare = emport.preparer(idPassage, paquet);

        assertThat(prepare.plan().octetsEstimes())
                .as("le volume s'annonce avant d'écrire, sinon on confirme à l'aveugle")
                .isPositive();
        assertThat(Files.exists(paquet))
                .as("et rien n'est écrit pour le savoir : c'est ce qui distingue un plan d'un essai")
                .isFalse();

        long octets = emport.ecrire(prepare);

        assertThat(octets).isEqualTo(Files.size(paquet));
        assertThat(prepare.plan().entrees())
                .as("ce qui a été écrit est ce qui avait été annoncé, manifeste compris")
                .hasSize(3);
    }

    @Test
    @DisplayName("L'aller-retour complet : l'avis revient signé, et se range à côté sans écraser")
    void l_aller_retour_complet_range_l_avis_a_cote() throws IOException {
        Path paquet = unPaquetDeDeuxSequences();
        selectionDao.marquerVerdict(
                selectionDao.findByPassage(idPassage).orElseThrow().id(),
                sequenceDao.findBySession(idSession).getFirst().id(),
                VerdictFichier.BON);

        // Chez le relecteur : il ouvre, juge autrement, et renvoie son avis.
        SourceDeDonnees posteB = posteQuiConnaitLaMemeCampagne();
        ServiceEmport chezB = serviceSur(posteB);
        SelectionDao selectionB = new SelectionDao(posteB);
        chezB.reprendre(paquet, Optional.of(RELECTEUR));
        Long selB = selectionB.findByPassage(passageDe(posteB)).orElseThrow().id();
        for (SequenceSelectionnee ligne : selectionB.listerSequences(selB)) {
            selectionB.marquerVerdict(selB, ligne.idSequence(), VerdictFichier.INEXPLOITABLE);
        }
        Path retour = dossier.resolve("avis.zip");
        chezB.renvoyerAvis(passageDe(posteB), retour, "chiro-pierre");

        PaquetOuvert ouvertChezA = OuvertureDePaquet.ouvrir(retour, Optional.of(RELECTEUR));
        assertThat(ouvertChezA.sequences())
                .as("le retour porte un avis, pas une nuit : aucune séquence ne refait le voyage")
                .isEmpty();
        ManifestePaquet manifesteDuRetour = ManifestePaquet.depuis(ouvertChezA.manifeste());
        assertThat(manifesteDuRetour.pseudoJugeur())
                .as("le pseudo voyage dans le manifeste, sinon l'expéditeur lirait le sien")
                .isEqualTo("chiro-pierre");
        assertThat(manifesteDuRetour.sequences()).hasSize(2);

        // Chez l'expéditeur : l'avis se range à côté du sien.
        ServiceEmport.BilanImportAvis bilan = emport.importerAvis(retour, false);

        assertThat(bilan.verdicts()).isEqualTo(2);
        assertThat(bilan.pseudoRelecteur()).isEqualTo("chiro-pierre");
        assertThat(selectionDao.listerSequences(
                        selectionDao.findByPassage(idPassage).orElseThrow().id()))
                .as("les deux verdicts coexistent : le nôtre n'a pas bougé")
                .anySatisfy(ligne -> {
                    assertThat(ligne.verdict()).isEqualTo(VerdictFichier.BON);
                    assertThat(ligne.verdictRelecteur()).isEqualTo(VerdictFichier.INEXPLOITABLE);
                    assertThat(ligne.pseudoRelecteur()).isEqualTo("chiro-pierre");
                });
    }

    @Test
    @DisplayName("Un paquet d'aller réimporté comme un avis se refuse : personne ne l'a signé")
    void un_paquet_non_signe_ne_s_importe_pas_comme_un_avis() throws IOException {
        Path aller = unPaquetDeDeuxSequences();

        assertThatThrownBy(() -> emport.importerAvis(aller, false))
                .as("un avis anonyme ne s'attribue pas : c'est la décision de l'ADR 4517")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("qui a jugé");
    }

    @Test
    @DisplayName("Un second avis ne remplace le premier qu'une fois le remplacement confirmé")
    void un_second_avis_attend_sa_confirmation() throws IOException {
        Path retour = unAvisRenvoyePar("claire");
        emport.importerAvis(retour, false);
        Path second = unAvisRenvoyePar("martin");

        assertThatThrownBy(() -> emport.importerAvis(second, false))
                .as("l'avis de claire tient tant que le remplacement n'est pas confirmé")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("claire");
        assertThat(selectionDao.listerSequences(
                        selectionDao.findByPassage(idPassage).orElseThrow().id()))
                .allSatisfy(ligne -> assertThat(ligne.pseudoRelecteur()).isEqualTo("claire"));

        emport.importerAvis(second, true);

        assertThat(selectionDao.listerSequences(
                        selectionDao.findByPassage(idPassage).orElseThrow().id()))
                .as("confirmé, le remplacement a eu lieu")
                .allSatisfy(ligne -> assertThat(ligne.pseudoRelecteur()).isEqualTo("martin"));
    }

    // --- montage -----------------------------------------------------------

    private ServiceEmport serviceSur(SourceDeDonnees s) {
        if (s == source) {
            selectionDao = new SelectionDao(s);
            sequenceDao = new SequenceDao(s);
        }
        return new ServiceEmport(
                new SelectionDao(s),
                new SequenceDao(s),
                new SessionDao(s),
                new PassageDao(s),
                new PointDao(s),
                new SiteDao(s),
                new UniteDeTravail(s));
    }

    /// Un paquet d'avis, tel qu'un relecteur le renverrait, jugé Inexploitable par `pseudo`.
    private Path unAvisRenvoyePar(String pseudo) throws IOException {
        Path aller = dossier.resolve("aller-" + pseudo + ".zip");
        if (selectionDao.findByPassage(idPassage).isEmpty()) {
            List<SequenceDEcoute> nuit = creerNuit(2);
            SelectionDEcoute selection =
                    selectionDao.insert(new SelectionDEcoute(null, MethodeSelection.MANUEL, 2, idPassage));
            selectionDao.attacherSequence(
                    new SequenceSelectionnee(selection.id(), nuit.get(0).id(), 0, false));
            selectionDao.attacherSequence(
                    new SequenceSelectionnee(selection.id(), nuit.get(1).id(), 1, false));
        }
        emport.composer(idPassage, aller);

        SourceDeDonnees poste = posteQuiConnaitLaMemeCampagne("poste-" + pseudo);
        ServiceEmport chez = serviceSur(poste);
        SelectionDao selectionLa = new SelectionDao(poste);
        chez.reprendre(aller, Optional.of(RELECTEUR));
        Long sel = selectionLa.findByPassage(passageDe(poste)).orElseThrow().id();
        for (SequenceSelectionnee ligne : selectionLa.listerSequences(sel)) {
            selectionLa.marquerVerdict(sel, ligne.idSequence(), VerdictFichier.INEXPLOITABLE);
        }
        Path retour = dossier.resolve("retour-" + pseudo + ".zip");
        chez.renvoyerAvis(passageDe(poste), retour, pseudo);
        return retour;
    }

    private Path unPaquetDeDeuxSequences() throws IOException {
        List<SequenceDEcoute> nuit = creerNuit(3);
        SelectionDEcoute selection =
                selectionDao.insert(new SelectionDEcoute(null, MethodeSelection.MANUEL, 2, idPassage));
        selectionDao.attacherSequence(
                new SequenceSelectionnee(selection.id(), nuit.get(0).id(), 0, false));
        selectionDao.attacherSequence(
                new SequenceSelectionnee(selection.id(), nuit.get(2).id(), 1, false));
        selectionDao.marquerVerdict(selection.id(), nuit.get(0).id(), VerdictFichier.BON);
        Path paquet = dossier.resolve("emport.zip");
        emport.composer(idPassage, paquet);
        return paquet;
    }

    private SourceDeDonnees posteQuiConnaitLaMemeCampagne() {
        return posteQuiConnaitLaMemeCampagne("poste-b");
    }

    /// Un poste relecteur nommé : deux relecteurs ne partagent pas la même base.
    private SourceDeDonnees posteQuiConnaitLaMemeCampagne(String emplacement) {
        SourceDeDonnees b = new SourceDeDonnees(new Workspace(dossier.resolve(emplacement)));
        new MigrationSchema(b).migrer();
        // Un second carré et un second point, pour que les filtres aient quelque chose à écarter.
        JeuDeDonneesPassage.dans(b)
                .utilisateur(ID_USER)
                .carre("999999")
                .nomSite("Leurre")
                .point("Z9")
                .position(40.0, 2.0)
                .enregistreur("0000000")
                .nuit(1, 2026, "2026-06-20")
                .heures("20:00:00", "06:00:00")
                .statut(StatutWorkflow.TRANSFORME)
                .semerPassage();
        JeuDeDonneesPassage.dans(b)
                .utilisateur(ID_USER)
                .carre("040962")
                .nomSite("Étang")
                .point("A1")
                .position(43.5, 5.4)
                .enregistreur("1925492")
                .nuit(1, 2026, "2026-06-20")
                .heures("20:00:00", "06:00:00")
                .statut(StatutWorkflow.TRANSFORME)
                .semerPassage();

        // Le régime de la copie signée suppose deux postes qui connaissent la MÊME nuit : le poste B
        // a donc les mêmes séquences, aux mêmes noms. Sans elles, il ne « connaît » pas la campagne.
        Long passageB = passageDe(b);
        Long sessionB = new SessionDao(b)
                .insert(new SessionDEnregistrement(null, "/wsB/sess", null, null, passageB))
                .id();
        Long originalB = new EnregistrementOriginalDao(b)
                .insert(new EnregistrementOriginal(null, "orig.wav", "/wsB/orig.wav", 5.0, 384000, null, sessionB))
                .id();
        SequenceDao sequencesB = new SequenceDao(b);
        for (int t = 0; t < 3; t++) {
            String nom = "Car040962-2026-Pass1-A1-" + String.format("%03d", t) + ".wav";
            sequencesB.insert(
                    new SequenceDEcoute(null, nom, originalB, t, 0.0, 5.0, "/wsB/" + nom, false, sessionB, null, null));
        }
        return b;
    }

    /// Le passage du carré 040962, et non « le premier venu » : le poste B en porte deux.
    private Long passageDe(SourceDeDonnees s) {
        Long idSite = new SiteDao(s)
                .findAll().stream()
                        .filter(site -> "040962".equals(site.numeroCarre()))
                        .findFirst()
                        .orElseThrow()
                        .id();
        Long idPoint = new PointDao(s).findBySite(idSite).getFirst().id();
        return new PassageDao(s)
                .trouverParPointAnneePassage(idPoint, 2026, 1)
                .orElseThrow()
                .id();
    }

    private List<SequenceDEcoute> creerNuit(int n) throws IOException {
        Path bruts = Files.createDirectories(dossier.resolve("sequences"));
        List<SequenceDEcoute> sequences = new java.util.ArrayList<>();
        for (int t = 0; t < n; t++) {
            String nom = "Car040962-2026-Pass1-A1-" + String.format("%03d", t) + ".wav";
            Path fichier = Files.writeString(bruts.resolve(nom), "contenu " + t);
            sequences.add(sequenceDao.insert(new SequenceDEcoute(
                    null, nom, idOriginal, t, 0.0, 5.0, fichier.toString(), false, idSession, null, null)));
        }
        return sequences;
    }
}
