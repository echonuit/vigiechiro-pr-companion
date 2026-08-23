package fr.univ_amu.iut.audit.model;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Commune;
import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import fr.univ_amu.iut.sites.model.dao.PointCommuneDao;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

/// Les **deux lectures** du département d'un point, confrontées (#2848).
///
/// Ce que ces tests établissent, et qui ne se devine pas : l'audit **ne sait pas** distinguer une
/// divergence légitime (un carré à cheval sur une limite de département) d'une divergence suspecte (un
/// GPS mal pointé). Les deux produisent **le même constat**, et c'est la décision : ce qu'il apporte est
/// de montrer l'écart à qui connaît le terrain, pas de le juger à sa place. D'où la sévérité `INFO` -
/// vérifiée ici, parce qu'un `AVERTISSEMENT` ferait rendre 1 à `audit-coherence` sur un carré de bord.
class AuditDepartementDuPointTest {

    private static final String ID_USER = "u-1";

    @TempDir
    Path dossier;

    private SourceDeDonnees source;
    private PointCommuneDao communes;
    private AuditDepartementDuPoint audit;

    @BeforeEach
    void preparer() {
        source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        communes = new PointCommuneDao(source);
        audit = new AuditDepartementDuPoint(new SiteDao(source), new PointDao(source), communes);
    }

    /// Sème un point sur un carré, et lui pose la commune donnée (ou aucune si `commune` est `null`).
    private long semer(String numeroCarre, String codePoint, Commune commune) {
        long idPoint = JeuDeDonneesPassage.dans(source)
                .utilisateur(ID_USER)
                .carre(numeroCarre)
                .point(codePoint)
                .semerSiteEtPoint()
                .idPoint();
        if (commune != null) {
            communes.definir(idPoint, commune);
        }
        return idPoint;
    }

    @Test
    @DisplayName("#2848 : les deux lectures se contredisent - un constat qui nomme les deux et leur source")
    void divergence_signalee() {
        semer("840962", "A1", new Commune("Aix-en-Provence", "13001"));

        List<ConstatAudit> constats = audit.auditer();

        assertThat(constats).hasSize(1);
        ConstatAudit constat = constats.getFirst();
        assertThat(constat.categorie()).isEqualTo(CategorieConstat.DEPARTEMENT_DIVERGENT);
        assertThat(constat.cible()).isEqualTo("840962 / A1");
        assertThat(constat.detail())
                .as("les deux nombres ET leur source passent EN TÊTE : la colonne « Détail » tronque, et "
                        + "ce constat est une comparaison - en montrer une moitié ne dit rien")
                .startsWith("Départements 13 (commune) et 84 (carré) :")
                .contains("Aix-en-Provence")
                .contains("840962");
    }

    @Test
    @DisplayName("#2848 : le constat est INFORMATIF - un carré de bord ne doit pas faire échouer l'audit")
    void severite_informative() {
        semer("840962", "A1", new Commune("Aix-en-Provence", "13001"));

        assertThat(audit.auditer()).extracting(ConstatAudit::severite).containsOnly(Severite.INFO);
        assertThat(new RapportAudit(audit.auditer()).aDesErreurs())
                .as("`audit-coherence` rend 1 sur une erreur : un chevauchement de département, qui est "
                        + "le cas NORMAL en bord de carré, casserait alors tous les scripts")
                .isFalse();
    }

    @Test
    @DisplayName("#2848 : divergence légitime et divergence suspecte donnent le MÊME constat")
    void legitime_et_suspecte_indiscernables() {
        // Bord de carré : 640380 est en Pyrénées-Atlantiques, et Bidart (64) comme Ahetze (64) y sont ;
        // un point de ce carré tombé dans les Landes (40) est parfaitement plausible.
        semer("640380", "A1", new Commune("Saint-Martin-de-Seignanx", "40252"));
        // Saisie erronée : un carré du Bas-Rhin dont le point serait à Marseille. Aucun chevauchement
        // n'explique 700 km.
        semer("670123", "B2", new Commune("Marseille", "13055"));

        List<ConstatAudit> constats = audit.auditer();

        assertThat(constats)
                .as("l'audit ne dispose d'aucune distance ni d'aucune géométrie de carré : il ne PEUT "
                        + "pas trier, et prétendre le contraire donnerait un faux sentiment de tri")
                .hasSize(2)
                .extracting(ConstatAudit::severite)
                .containsOnly(Severite.INFO);
    }

    @Test
    @DisplayName("#2848 : les deux lectures concordent - silence")
    void concordance_silencieuse() {
        semer("130711", "A1", new Commune("Aix-en-Provence", "13001"));

        assertThat(audit.auditer()).isEmpty();
    }

    @Test
    @DisplayName("#2848 : un point sans commune résolue ne produit rien - il n'y a rien à confronter")
    void commune_non_resolue() {
        semer("840962", "A1", null);

        assertThat(audit.auditer())
                .as("une commune non résolue est un état normal (point sans GPS, rattrapage jamais "
                        + "lancé) : en faire un constat noierait l'audit dès la première base")
                .isEmpty();
    }

    @Test
    @DisplayName("#2848 : un numéro de carré illisible ne produit rien - il n'y a pas de PREMIÈRE lecture")
    void carre_illisible() {
        // Le pendant de la commune non résolue, sur l'autre lecture. Un numéro trop court ne dit aucun
        // département : le signaler comme divergent accuserait le point d'un défaut qui est celui du
        // carré, et qui relève d'un autre contrôle (R1, six chiffres).
        semer("6", "A1", new Commune("Aix-en-Provence", "13001"));

        assertThat(audit.auditer()).isEmpty();
    }

    @Test
    @DisplayName("#3298 : un carré d'outre-mer ne produit AUCUN constat - « 00 » n'est pas un département")
    void carre_outre_mer() {
        // Mesuré, pas supposé : le catalogue de la plateforme porte 307 carrés « 00xxxx », et AUCUN
        // « 97xxxx ». « 000294 » est à Saint-Joseph et « 001293 » à Salazie, La Réunion. L'audit
        // signalait « Départements 974 (commune) et 00 (carré) » à chaque fois - une divergence que
        // rien sur le terrain n'aurait pu faire taire, puisque 00 n'est pas un département.
        semer("000294", "Z2", new Commune("Saint-Joseph", "97412"));
        semer("981234", "Z1", new Commune("Marseille", "13055"));

        assertThat(audit.auditer())
                .as("un préfixe qui ne désigne pas de département ne porte pas de première lecture : "
                        + "c'est le même silence que devant un numéro trop court")
                .isEmpty();
    }

    @Test
    @DisplayName("#2848 : la Corse ne diverge pas d'elle-même - 20 côté carré, 2A/2B côté INSEE")
    void corse_ne_diverge_pas() {
        semer("200001", "A1", new Commune("Ajaccio", "2A004"));
        semer("200002", "B1", new Commune("Bastia", "2B033"));

        assertThat(audit.auditer())
                .as("comparer les chaînes telles quelles ferait de CHAQUE point corse une divergence")
                .isEmpty();
    }

    @Test
    @DisplayName("#2848 : outre-mer, le numéro de carré ne dit pas quel 97x - l'audit s'abstient")
    void outre_mer_abstention() {
        semer("970123", "A1", new Commune("Sainte-Rose", "97108"));

        assertThat(audit.auditer()).isEmpty();
    }

    @Test
    @DisplayName("#2848 : plusieurs points d'un même carré sont jugés un par un")
    void chaque_point_pour_soi() {
        semer("840962", "A1", new Commune("Aix-en-Provence", "13001"));
        semer("840962", "A2", new Commune("Avignon", "84007"));

        assertThat(audit.auditer())
                .as("le carré porte une seule lecture, mais chaque point porte la sienne : un constat "
                        + "par carré confondrait le point fautif avec ses voisins")
                .extracting(ConstatAudit::cible)
                .containsExactly("840962 / A1");
    }

    @Test
    @DisplayName("#4280 : l'audit lit points et communes par LOT, pas site par site")
    void l_audit_lit_par_lot() {
        semer("010203", "A1", new Commune("Ain-ville", "01"));
        semer("040506", "B1", new Commune("Alpes-ville", "04"));
        semer("070809", "C1", new Commune("Ardeche-ville", "07"));

        PointDao points = Mockito.spy(new PointDao(source));
        PointCommuneDao communesSurveillees = Mockito.spy(communes);
        AuditDepartementDuPoint surveille =
                new AuditDepartementDuPoint(new SiteDao(source), points, communesSurveillees);
        Mockito.clearInvocations(points, communesSurveillees);

        surveille.auditer();

        // ⚠️ Le garde compte des REQUÊTES, pas des millisecondes : un butoir en temps se noierait dans la
        // variance de la machine. Le défaut mesuré (#4280) : une requête par site pour ses points, puis
        // une par point pour sa commune - 130 ms à cent cinquante carrés, contre 3 ms lu par lot.
        Mockito.verify(points, Mockito.never()).findBySite(Mockito.any());
        Mockito.verify(communesSurveillees, Mockito.never()).pour(Mockito.anyLong());
        Mockito.verify(points, Mockito.times(1)).findParSites(Mockito.any());
        Mockito.verify(communesSurveillees, Mockito.times(1)).findAll();
    }
}
