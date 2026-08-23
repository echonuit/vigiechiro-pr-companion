package fr.univ_amu.iut.sites.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.PointVigieChiro;
import fr.univ_amu.iut.commun.api.ProfilVigieChiro;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.api.SiteVigieChiro;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.sites.model.dao.PointCommuneDao;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import fr.univ_amu.iut.sites.model.dao.SiteTiersDao;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// **Rapatrier un carré par son numéro** (#3806), sans l'avoir déposé ni le posséder.
///
/// ## Le cercle que ce service casse
///
/// Une nuit *opportuniste* se prépare en déclarant le carré puis le point, avant de déposer. Mais le
/// dépôt exige que le site local porte un **lien** vers son homologue plateforme
/// (`SynchronisationParticipation#creerPour`), et la synchronisation qui pose ce lien part de
/// `GET /moi/participations` : elle n'atteint donc que les carrés où une nuit est **déjà** déposée.
///
/// > Déposer était la seule chose qui aurait créé la participation qui aurait rendu le dépôt possible.
///
/// Mesuré le 2026-08-15 sur un compte réel : `/moi/sites` rend **0** là où `/moi/participations` rend un
/// site, dont le propriétaire est quelqu'un d'autre - les possesseurs de carrés sont peu nombreux, et la
/// majorité des observateurs déposent sur des carrés qui ne leur appartiennent pas.
///
/// `GET /sites?q=<carré>` rend ce site à qui le demande, avec ses localités positionnées : de quoi poser
/// le lien **avant** tout dépôt.
@ExtendWith(MockitoExtension.class)
class RapatriementCarreTest {

    private static final String ID_USER = "u-1";
    private static final String CARRE = "130711";

    @TempDir
    Path dossier;

    @Mock
    private ClientVigieChiro client;

    private SiteDao siteDao;
    private PointDao pointDao;
    private LienVigieChiroDao liens;
    private SiteTiersDao siteTiers;
    private ServiceSites service;
    private RapatriementCarre rapatriement;

    @BeforeEach
    void preparer() {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur"));
        siteDao = new SiteDao(source);
        pointDao = new PointDao(source);
        PointCommuneDao communeDao = new PointCommuneDao(source);
        service = new ServiceSites(
                siteDao,
                pointDao,
                new PassageDao(source),
                new HorlogeFigee(LocalDate.of(2026, 8, 15)),
                communeDao,
                () -> {});
        liens = new LienVigieChiroDao(source);
        siteTiers = new SiteTiersDao(source);
        rapatriement = new RapatriementCarre(
                client,
                new ImportSiteDistant(
                        siteDao,
                        service,
                        liens,
                        siteTiers,
                        ID_USER,
                        new ServiceCommunes(pointDao, communeDao, position -> Optional.empty())));
    }

    private static SiteVigieChiro siteDistant(List<PointVigieChiro> points, String proprietaire) {
        return new SiteVigieChiro("6a49", "Vigiechiro - Point Fixe-" + CARRE, true, CARRE, proprietaire, points);
    }

    @Test
    @DisplayName("#3806 : un carré rapatrié est RATTACHÉ, donc le dépôt ne le refusera plus")
    void un_carre_rapatrie_est_rattache() {
        when(client.chercherCarre(CARRE))
                .thenReturn(ReponseApi.succes(
                        List.of(siteDistant(List.of(new PointVigieChiro("Z41", 43.51, 5.45)), "un-tiers"))));

        RapatriementCarre.Resultat resultat =
                rapatriement.rapatrier(new SouhaitDeclaration(CARRE, Protocole.STANDARD, null, null));

        // C'est le fait qui porte tout le lot : sans ce lien, `SynchronisationParticipation#creerPour`
        // refuse la participation avec « Site non rattaché à Vigie-Chiro », et le téléversement s'arrête
        // là (#3463) - alors même que déposer est ce qui aurait créé le rattachement.
        assertThat(resultat).isInstanceOf(RapatriementCarre.Resultat.Rapatrie.class);
        Site local = siteDao.findByUtilisateur(ID_USER).getFirst();
        assertThat(liens.objectidPour(LienVigieChiro.ENTITE_SITE, String.valueOf(local.id())))
                .as("le lien vers l'identifiant plateforme est ce que le dépôt exige, et rien d'autre")
                .contains("6a49");
    }

    @Test
    @DisplayName("#3806 : les localités arrivent POSITIONNÉES : il n'y a plus rien à ressaisir")
    void les_localites_arrivent_positionnees() {
        when(client.chercherCarre(CARRE))
                .thenReturn(ReponseApi.succes(List.of(siteDistant(
                        List.of(
                                new PointVigieChiro("Z1", 43.522194, 5.465893),
                                new PointVigieChiro("Z41", 43.514558, 5.451322)),
                        "un-tiers"))));

        rapatriement.rapatrier(new SouhaitDeclaration(CARRE, Protocole.STANDARD, null, null));

        // Le retour d'origine tenait en une phrase : « j'ai dû recréer Z1 manuellement en le positionnant
        // (en le choisissant dans la liste, il n'était pas prépositionné) ».
        Site local = siteDao.findByUtilisateur(ID_USER).getFirst();
        assertThat(pointDao.findBySite(local.id()))
                .extracting(PointDEcoute::code, PointDEcoute::latitude, PointDEcoute::longitude)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("Z1", 43.522194, 5.465893),
                        org.assertj.core.groups.Tuple.tuple("Z41", 43.514558, 5.451322));
    }

    @Test
    @DisplayName("#3806 : le compte annonce les points POSÉS, pas ceux que la plateforme a envoyés")
    void le_compte_annonce_les_points_reellement_poses() {
        when(client.chercherCarre(CARRE))
                .thenReturn(ReponseApi.succes(List.of(siteDistant(
                        List.of(
                                new PointVigieChiro("Z1", 43.522194, 5.465893),
                                // Code hors R2 : `ajouterPointSynchronise` le refuse, et l'import l'ignore
                                // en best-effort pour ne pas perdre les quarante autres.
                                new PointVigieChiro("pas-un-code", 43.51, 5.45)),
                        "un-tiers"))));

        RapatriementCarre.Resultat resultat =
                rapatriement.rapatrier(new SouhaitDeclaration(CARRE, Protocole.STANDARD, null, null));

        // Annoncer « 2 points positionnés » quand un seul est en base ferait chercher longtemps un point
        // qui n'existe pas. Le compte rendu dit ce qui EST, pas ce qui a été tenté.
        Site local = siteDao.findByUtilisateur(ID_USER).getFirst();
        assertThat(pointDao.findBySite(local.id())).hasSize(1);
        assertThat(resultat)
                .asInstanceOf(
                        org.assertj.core.api.InstanceOfAssertFactories.type(RapatriementCarre.Resultat.Rapatrie.class))
                .extracting(RapatriementCarre.Resultat.Rapatrie::points)
                .isEqualTo(1);
    }

    @Test
    @DisplayName("#3806 : entre plusieurs protocoles, on récupère le POINT FIXE, pas le premier venu")
    void entre_plusieurs_protocoles_on_prend_le_point_fixe() {
        when(client.chercherCarre(CARRE))
                .thenReturn(ReponseApi.succes(List.of(
                        new SiteVigieChiro(
                                "routier", "Vigie-chiro - Routier-" + CARRE, true, CARRE, "un-tiers", List.of()),
                        new SiteVigieChiro(
                                "pointfixe",
                                "Vigiechiro - Point Fixe-" + CARRE,
                                true,
                                CARRE,
                                "un-tiers",
                                List.of(new PointVigieChiro("Z41", 43.51, 5.45))))));

        rapatriement.rapatrier(new SouhaitDeclaration(CARRE, Protocole.STANDARD, null, null));

        // Un numéro de carré ne désigne pas un site : le même carré peut exister en Point Fixe, en
        // Pédestre et en Routier. Se rattacher au premier venu enverrait la nuit au mauvais endroit -
        // le défaut que ce chantier corrige, reproduit par sa propre correction.
        Site local = siteDao.findByUtilisateur(ID_USER).getFirst();
        assertThat(liens.objectidPour(LienVigieChiro.ENTITE_SITE, String.valueOf(local.id())))
                .contains("pointfixe");
    }

    @Test
    @DisplayName("#3806 : carré présent sous un protocole que l'application ne gère pas : on le DIT")
    void carre_sous_un_protocole_non_gere() {
        when(client.chercherCarre(CARRE))
                .thenReturn(ReponseApi.succes(List.of(new SiteVigieChiro(
                        "routier", "Vigie-chiro - Routier-" + CARRE, true, CARRE, "un-tiers", List.of()))));

        RapatriementCarre.Resultat resultat =
                rapatriement.rapatrier(new SouhaitDeclaration(CARRE, Protocole.STANDARD, null, null));

        // Se taire ou dire « inexistant » serait faux : le carré est bien là, c'est le protocole qui ne
        // suit pas. L'utilisateur doit pouvoir comprendre pourquoi son numéro « existe » sans être
        // récupérable.
        assertThat(resultat)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(
                        RapatriementCarre.Resultat.AutreProtocole.class))
                .satisfies(autre -> assertThat(autre.titres()).containsExactly("Vigie-chiro - Routier-" + CARRE));
        assertThat(siteDao.findByUtilisateur(ID_USER)).isEmpty();
    }

    @Test
    @DisplayName("#3806 : le site créé porte le protocole choisi dans la modale, pas un protocole par défaut")
    void le_site_cree_porte_le_protocole_choisi() {
        when(client.chercherCarre(CARRE))
                .thenReturn(ReponseApi.succes(
                        List.of(siteDistant(List.of(new PointVigieChiro("Z41", 43.51, 5.45)), "un-tiers"))));

        rapatriement.rapatrier(new SouhaitDeclaration(CARRE, Protocole.RECHERCHE, null, null));

        // « PointFixeRecherche » n'est pas un autre protocole côté plateforme : c'est une variante LOCALE
        // (R3/R4 muettes, dates libres). Le rapatriement doit donc respecter ce que l'utilisateur a choisi
        // plutôt que d'imposer STANDARD comme le fait la synchronisation périodique.
        assertThat(siteDao.findByUtilisateur(ID_USER).getFirst().protocole()).isEqualTo(Protocole.RECHERCHE);
    }

    @Test
    @DisplayName("#3806 : un carré introuvable ne crée rien")
    void un_carre_introuvable_ne_cree_rien() {
        when(client.chercherCarre("999999")).thenReturn(ReponseApi.succes(List.of()));

        assertThat(rapatriement.rapatrier(new SouhaitDeclaration("999999", Protocole.STANDARD, null, null)))
                .isInstanceOf(RapatriementCarre.Resultat.Inexistant.class);
        assertThat(siteDao.findByUtilisateur(ID_USER)).isEmpty();
    }

    @Test
    @DisplayName("#3806 : hors connexion, on ne crée rien et on ne prétend pas avoir cherché")
    void hors_connexion_rien_n_est_cree() {
        when(client.chercherCarre(CARRE)).thenReturn(ReponseApi.nonConnecte());

        assertThat(rapatriement.rapatrier(new SouhaitDeclaration(CARRE, Protocole.STANDARD, null, null)))
                .isInstanceOf(RapatriementCarre.Resultat.Indisponible.class);
        assertThat(siteDao.findByUtilisateur(ID_USER)).isEmpty();
    }

    @Test
    @DisplayName("#4233 : trois empêchements, trois messages - le programme ne fait pas deviner")
    void chaque_empechement_dit_sa_propre_cause() {
        SouhaitDeclaration souhait = new SouhaitDeclaration(CARRE, Protocole.STANDARD, null, null);

        when(client.chercherCarre(CARRE)).thenReturn(ReponseApi.nonConnecte());
        String sansJeton = rapatriement.rapatrier(souhait).message();

        when(client.chercherCarre(CARRE)).thenReturn(ReponseApi.injoignable("délai dépassé"));
        String injoignable = rapatriement.rapatrier(souhait).message();

        when(client.chercherCarre(CARRE)).thenReturn(ReponseApi.refuse(401, "jeton expiré"));
        String refuse = rapatriement.rapatrier(souhait).message();

        // ⚠️ Le message nommait DEUX causes à la fois - « Vigie-Chiro est injoignable ou vous n'êtes pas
        // connecté » - alors que le `switch` les distingue depuis toujours. Il faisait douter d'une
        // connexion qui était bonne, et laissait l'utilisateur trancher ce que le programme savait déjà.
        assertThat(sansJeton).contains("pas connecté").doesNotContain("injoignable");
        assertThat(injoignable).contains("injoignable").doesNotContain("pas connecté");
        assertThat(refuse).contains("refusé").contains("401");

        // Et les trois disent le geste qui répare, chacun le sien.
        assertThat(sansJeton).contains("Connectez-vous");
        assertThat(injoignable).contains("Réessayez");
        assertThat(refuse).contains("jeton");

        assertThat(List.of(sansJeton, injoignable, refuse))
                .as("trois causes distinctes ne peuvent pas rendre le même message")
                .doesNotHaveDuplicates()
                .allSatisfy(message -> assertThat(message).contains("Rien n'a été créé"));
    }

    @Test
    @DisplayName("#3806 : ce que l'utilisateur a saisi survit au rapatriement")
    void la_saisie_de_l_utilisateur_survit() {
        when(client.chercherCarre(CARRE))
                .thenReturn(ReponseApi.succes(
                        List.of(siteDistant(List.of(new PointVigieChiro("Z41", 43.51, 5.45)), "un-tiers"))));

        rapatriement.rapatrier(
                new SouhaitDeclaration(CARRE, Protocole.STANDARD, "Étang de la Tuilière", "Accès par le chemin"));

        // L'utilisateur avait rempli le formulaire avant de découvrir que le carré existait déjà. Écraser
        // son nom par le titre de la plateforme - « Vigiechiro - Point Fixe-130711 » - lui reprendrait ce
        // qu'il vient d'écrire, et le remplacerait par un libellé technique.
        Site local = siteDao.findByUtilisateur(ID_USER).getFirst();
        assertThat(local.nomConvivial()).isEqualTo("Étang de la Tuilière");
        assertThat(local.commentaire()).isEqualTo("Accès par le chemin");
    }

    @Test
    @DisplayName("#3806 : sans saisie, le site prend le titre de la plateforme")
    void sans_saisie_le_titre_plateforme_sert_de_nom() {
        when(client.chercherCarre(CARRE))
                .thenReturn(ReponseApi.succes(
                        List.of(siteDistant(List.of(new PointVigieChiro("Z41", 43.51, 5.45)), "un-tiers"))));

        rapatriement.rapatrier(new SouhaitDeclaration(CARRE, Protocole.STANDARD, null, null));

        // Un nom vaut mieux que pas de nom : à défaut de celui de l'utilisateur, celui de la plateforme
        // dit au moins de quel carré et de quel protocole il s'agit.
        assertThat(siteDao.findByUtilisateur(ID_USER).getFirst().nomConvivial())
                .isEqualTo("Vigiechiro - Point Fixe-" + CARRE);
    }

    @Test
    @DisplayName("#3806 : les trois comptes rendus disent ce qui vient de se passer, et quoi faire ensuite")
    void les_comptes_rendus_disent_quoi_faire() {
        when(client.chercherCarre(CARRE))
                .thenReturn(ReponseApi.succes(
                        List.of(siteDistant(List.of(new PointVigieChiro("Z41", 43.51, 5.45)), "un-tiers"))));
        RapatriementCarre.Resultat rapatrie =
                rapatriement.rapatrier(new SouhaitDeclaration(CARRE, Protocole.STANDARD, null, null));

        // Ce que l'utilisateur lit est le produit : ces messages sont la seule chose qui lui dise que son
        // carré est rattaché, donc que son dépôt passera.
        assertThat(rapatrie.message()).contains(CARRE).contains("1 point").contains("déposer");
        assertThat(rapatrie.severite()).isEqualTo(Severite.SUCCES);

        when(client.chercherCarre("999999")).thenReturn(ReponseApi.succes(List.of()));
        RapatriementCarre.Resultat inexistant =
                rapatriement.rapatrier(new SouhaitDeclaration("999999", Protocole.STANDARD, null, null));
        assertThat(inexistant.message()).contains("n'existe pas").contains("portail");
        assertThat(inexistant.severite()).isEqualTo(Severite.INFO);

        when(client.chercherCarre("640380"))
                .thenReturn(ReponseApi.succes(List.of(new SiteVigieChiro(
                        "routier", "Vigie-chiro - Routier-640380", true, "640380", "un-tiers", List.of()))));
        RapatriementCarre.Resultat autre =
                rapatriement.rapatrier(new SouhaitDeclaration("640380", Protocole.STANDARD, null, null));
        assertThat(autre.message()).contains("pas en Point Fixe").contains("Routier");
        assertThat(autre.severite()).isEqualTo(Severite.AVERTISSEMENT);
    }

    @Test
    @DisplayName("#3806 : sur un carré DÉJÀ déclaré à la main, on complète sans écraser")
    void sur_un_carre_deja_declare_on_complete() {
        Site local = service.creerSite(CARRE, "Mon carré", Protocole.STANDARD, null, ID_USER);
        service.ajouterPoint(local.id(), "Z41", 43.9, 5.9, "posé à la main");
        when(client.chercherCarre(CARRE))
                .thenReturn(ReponseApi.succes(List.of(siteDistant(
                        List.of(
                                new PointVigieChiro("Z41", 43.51, 5.45),
                                new PointVigieChiro("Z1", 43.52, 5.46),
                                new PointVigieChiro("Z2", 43.53, 5.47)),
                        "un-tiers"))));

        RapatriementCarre.Resultat resultat =
                rapatriement.rapatrier(new SouhaitDeclaration(CARRE, Protocole.STANDARD, null, null));

        // C'est le parcours réel : l'observateur avait créé son carré et son point à la main, faute de
        // pouvoir les récupérer. Le rapatriement doit le rattacher et compléter ce qui manque, SANS
        // déplacer le point qu'il a positionné lui-même.
        assertThat(pointDao.findBySite(local.id()))
                .extracting(PointDEcoute::code)
                .containsExactlyInAnyOrder("Z41", "Z1", "Z2");
        assertThat(pointDao.findBySite(local.id()).stream()
                        .filter(point -> "Z41".equals(point.code()))
                        .findFirst()
                        .orElseThrow()
                        .latitude())
                .as("le point saisi à la main garde SA position")
                .isEqualTo(43.9);
        assertThat(resultat)
                .asInstanceOf(
                        org.assertj.core.api.InstanceOfAssertFactories.type(RapatriementCarre.Resultat.Rapatrie.class))
                .extracting(RapatriementCarre.Resultat.Rapatrie::points)
                .as("deux points ajoutés, pas trois : Z41 était déjà là")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("#3806 : un carré qui appartient à un tiers est marqué comme tel")
    void un_carre_de_tiers_est_marque() {
        when(client.moi()).thenReturn(ReponseApi.succes(new ProfilVigieChiro("moi", "Testeur", "Observateur")));
        when(client.chercherCarre(CARRE))
                .thenReturn(ReponseApi.succes(
                        List.of(siteDistant(List.of(new PointVigieChiro("Z41", 43.51, 5.45)), "quelqu-un-d-autre"))));

        rapatriement.rapatrier(new SouhaitDeclaration(CARRE, Protocole.STANDARD, null, null));

        // Le marquage décide de la suite : les nuits d'un carré de tiers sont des participations
        // opportunistes (#2525). C'est le cas MAJORITAIRE - les possesseurs de carrés sont peu nombreux -
        // donc le rapatriement le pose comme le fait la synchronisation périodique.
        Site local = siteDao.findByUtilisateur(ID_USER).getFirst();
        assertThat(siteTiers.estTiers(local.id())).isTrue();
    }

    @Test
    @DisplayName("#3806 : avec plusieurs carrés locaux, le compte rendu parle du BON")
    void avec_plusieurs_carres_locaux_le_bon_est_rendu() {
        service.creerSite("640380", "Un autre carré", Protocole.STANDARD, null, ID_USER);
        when(client.chercherCarre(CARRE))
                .thenReturn(ReponseApi.succes(
                        List.of(siteDistant(List.of(new PointVigieChiro("Z41", 43.51, 5.45)), "un-tiers"))));

        RapatriementCarre.Resultat resultat =
                rapatriement.rapatrier(new SouhaitDeclaration(CARRE, Protocole.STANDARD, null, null));

        // La fiche qui s'ouvre derrière est celle du site rendu ici : se tromper de site enverrait
        // l'utilisateur sur un carré qu'il n'a pas demandé, avec un compte rendu qui parle d'un autre.
        assertThat(resultat)
                .asInstanceOf(
                        org.assertj.core.api.InstanceOfAssertFactories.type(RapatriementCarre.Resultat.Rapatrie.class))
                .extracting(rapatrie -> rapatrie.site().numeroCarre())
                .isEqualTo(CARRE);
    }

    @Test
    @DisplayName("#3806 : profil illisible : on ne présume PAS que le carré est celui d'un tiers")
    void profil_illisible_pas_de_presomption_de_tiers() {
        when(client.moi()).thenReturn(ReponseApi.nonConnecte());
        when(client.chercherCarre(CARRE))
                .thenReturn(ReponseApi.succes(
                        List.of(siteDistant(List.of(new PointVigieChiro("Z41", 43.51, 5.45)), "quelqu-un-d-autre"))));

        rapatriement.rapatrier(new SouhaitDeclaration(CARRE, Protocole.STANDARD, null, null));

        // Marquer « carré d'un tiers » sans savoir qui l'on est rendrait toutes les nuits opportunistes
        // sur la foi d'une comparaison avec rien. Sans preuve, on ne présume pas (#2525).
        Site local = siteDao.findByUtilisateur(ID_USER).getFirst();
        assertThat(siteTiers.estTiers(local.id())).isFalse();
    }

    @Test
    @DisplayName("#3806 : dans l'application complète, le rapatriement est bien LIÉ")
    void le_rapatriement_est_lie_dans_l_application_complete(@TempDir Path espaceDeTravail) {
        System.setProperty("vigiechiro.workspace", espaceDeTravail.toString());
        try {
            Injector injecteur = Guice.createInjector(RacineInjecteur.modules());
            // L'injecteur applicatif lit la base dès qu'on lui demande un objet qui en dépend : elle doit
            // exister avant, comme dans l'application.
            new MigrationSchema(injecteur.getInstance(SourceDeDonnees.class)).migrer();

            // Sans ce `setBinding`, l'`Optional` reste vide, le ViewModel n'offre pas le geste, et le
            // bouton « Récupérer ce carré » n'apparaît JAMAIS - dans l'application seulement, là où
            // aucun test de vue ne regarde. La mutation qui retire cette ligne ne faisait rougir personne.
            assertThat(injecteur.getInstance(Key.get(new TypeLiteral<Optional<RapatriementCarre>>() {})))
                    .as("la feature « carre-existant » lie la recherche ET sa suite")
                    .isPresent();
            assertThat(injecteur.getInstance(Key.get(new TypeLiteral<Optional<RechercheCarreExistant>>() {})))
                    .isPresent();
        } finally {
            System.clearProperty("vigiechiro.workspace");
        }
    }
}
