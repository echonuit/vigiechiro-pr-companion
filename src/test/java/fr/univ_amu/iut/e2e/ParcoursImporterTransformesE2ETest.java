package fr.univ_amu.iut.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import com.google.inject.Injector;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.FichierWav;
import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.importation.model.ServiceImportReference;
import fr.univ_amu.iut.importation.model.ServiceImportReference.ResultatImportReference;
import fr.univ_amu.iut.passage.model.DisponibiliteAudio;
import fr.univ_amu.iut.passage.model.ModeRebranchement;
import fr.univ_amu.iut.passage.model.RapportReactivation;
import fr.univ_amu.iut.passage.model.ServiceDisponibiliteAudio;
import fr.univ_amu.iut.passage.model.ServiceReactivationPassage;
import fr.univ_amu.iut.passage.model.VoieReactivation;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// **Test E2E de la couture « référence → sommeil → réveil »** (#2425, chantier #2258).
///
/// Le geste #2433 crée un passage qui **référence** des transformés déjà présents ; l'identité (empreinte)
/// est posée à l'inscription. Ce parcours exerce la chaîne entière que ni le test du modèle ni celui de la
/// CLI ne traversent d'un bout à l'autre :
///
/// 1. **référencer** : importer un dossier externe de transformés, sans copie → le passage est **écoutable**
///    (COMPLETE), les séquences pointent le dossier externe ;
/// 2. **sommeil** : le support déménage (le dossier change de chemin) → l'audio devient **ABSENTE**, sans que
///    rien ne soit corrompu (la base pointe simplement un chemin qui n'existe plus) ;
/// 3. **réveil** : réactiver **par référence** depuis le nouvel emplacement → l'identité est **revérifiée**
///    (empreinte posée à l'import), les chemins sont rebranchés sans copie, l'audio redevient **COMPLETE**.
///
/// C'est la preuve que l'empreinte calculée à l'inscription (et non à une réactivation antérieure) suffit à
/// la garde d'identité au réveil : un premier import référencé **établit** la référence que la réactivation
/// **vérifie** ensuite.
class ParcoursImporterTransformesE2ETest {

    private static final String ID_USER = "u-e2e-tr";
    private static final String SERIE = "1925492";
    private static final int FREQUENCE_ENTETE = 38_400; // Fe/10 d'une séquence déjà transformée
    private static final int TRAMES = 6_000;

    @Test
    @DisplayName(
            "Référence → sommeil → réveil : l'audio référencé s'endort quand le support part, se réveille au retour")
    void reference_sommeil_reveil() throws IOException {
        Injector injector = injecteurNeuf();
        SourceDeDonnees source = injector.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();
        Long idPoint = prepararerPoint(source);

        ServiceImportReference importReference = injector.getInstance(ServiceImportReference.class);
        ServiceDisponibiliteAudio disponibilite = injector.getInstance(ServiceDisponibiliteAudio.class);
        SessionDao sessionDao = injector.getInstance(SessionDao.class);
        SequenceDao sequenceDao = injector.getInstance(SequenceDao.class);

        // 1. Référencer un dossier externe de transformés (aucune copie dans l'espace de travail).
        Path support = preparerDossierTransforme(Files.createTempDirectory("vc-e2e-tr-support"));
        ResultatImportReference resultat =
                importReference.importer(support, idPoint, 2026, 1, true, p -> {}, JetonAnnulation.neutre());
        long idPassage = resultat.idPassage();

        assertThat(disponibilite.disponibilite(idPassage))
                .as("les WAV sont là où on les a référencés : le passage est écoutable")
                .isEqualTo(DisponibiliteAudio.COMPLETE);
        Long idSession = sessionDao.trouverParPassage(idPassage).orElseThrow().id();
        assertThat(sequenceDao.findBySession(idSession))
                .allSatisfy(s -> assertThat(s.cheminFichier()).startsWith(support.toString()));

        // 2. Le support déménage : l'ancien chemin n'existe plus. La base pointe un chemin absent - sommeil,
        //    pas corruption.
        Path nouvelEmplacement = support.resolveSibling(support.getFileName() + "-deplace");
        Files.move(support, nouvelEmplacement);
        disponibilite.invalider(idPassage);

        assertThat(disponibilite.disponibilite(idPassage))
                .as("le support est parti : l'audio référencé devient absent (endormi)")
                .isEqualTo(DisponibiliteAudio.ABSENTE);

        // 3. Réveil : réactiver PAR RÉFÉRENCE depuis le nouvel emplacement. La voie TRANSFORMES retrouve les
        //    séquences par leur nom, la cascade d'identité les accepte sur l'empreinte posée à l'import, et le
        //    poseur REFERENCE rebranche les chemins sans copier.
        RapportReactivation reveil = injector.getInstance(ServiceReactivationPassage.class)
                .reactiver(
                        idPassage,
                        nouvelEmplacement,
                        ModeRebranchement.REFERENCE,
                        p -> {},
                        p -> {},
                        JetonAnnulation.neutre());

        assertThat(reveil.voie())
                .as("le dossier ne contient que des transformés : voie de rebranchement des tranches")
                .isEqualTo(VoieReactivation.TRANSFORMES);
        assertThat(reveil.complete()).as("tout est revenu").isTrue();
        assertThat(disponibilite.disponibilite(idPassage))
                .as("l'audio est de nouveau écoutable, au nouvel emplacement")
                .isEqualTo(DisponibiliteAudio.COMPLETE);
        assertThat(sequenceDao.findBySession(idSession))
                .as("les chemins pointent le nouvel emplacement, toujours par référence (aucune copie)")
                .allSatisfy(s -> assertThat(s.cheminFichier()).startsWith(nouvelEmplacement.toString()));
        assertThat(wavSousLaRacine(injector.getInstance(fr.univ_amu.iut.commun.model.Workspace.class)
                        .racine()))
                .as("le réveil n'a rien recopié dans l'espace de travail")
                .isEmpty();
    }

    private static Injector injecteurNeuf() throws IOException {
        Path workspace = Files.createTempDirectory("vc-e2e-tr-ws");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        return Guice.createInjector(RacineInjecteur.modules());
    }

    private static Long prepararerPoint(SourceDeDonnees source) {
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur E2E"));
        Site site = new SiteDao(source)
                .insert(new Site(null, "640380", "Étang", Protocole.STANDARD, null, "2026-05-31", ID_USER));
        return new PointDao(source)
                .insert(new PointDEcoute(null, "Z1", 43.5, 5.4, null, site.id()))
                .id();
    }

    /// Dossier externe de transformés : deux originaux (2 tranches + 1), noms R6 horodatés portant la série.
    private static Path preparerDossierTransforme(Path dossier) throws IOException {
        String base = "Car640380-2026-Pass1-Z1-PaRecPR" + SERIE + "_20260422_";
        ecrireWav(dossier.resolve(base + "203922_000.wav"), 1);
        ecrireWav(dossier.resolve(base + "203922_001.wav"), 2);
        ecrireWav(dossier.resolve(base + "204326_000.wav"), 3);
        return dossier;
    }

    private static void ecrireWav(Path fichier, int germe) throws IOException {
        byte[] pcm = new byte[TRAMES * 2];
        int valeur = germe;
        for (int n = 0; n < TRAMES; n++) {
            valeur = valeur * 31 + 17;
            short amplitude = (short) (valeur % 8000);
            pcm[2 * n] = (byte) (amplitude & 0xFF);
            pcm[2 * n + 1] = (byte) ((amplitude >> 8) & 0xFF);
        }
        FichierWav.ecrire(fichier, 1, FREQUENCE_ENTETE, 16, pcm, 0, pcm.length);
    }

    private static List<String> wavSousLaRacine(Path racine) throws IOException {
        if (!Files.isDirectory(racine)) {
            return List.of();
        }
        try (var flux = Files.walk(racine)) {
            return flux.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(nom -> nom.toLowerCase(java.util.Locale.ROOT).endsWith(".wav"))
                    .toList();
        }
    }
}
