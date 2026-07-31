package fr.univ_amu.iut.cliquet;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// **Cliquet des semeurs de topologie** (#2865) : les fichiers qui créent un site et un point d'écoute à
/// la main, sans aller jusqu'à la nuit.
///
/// ## Pourquoi ce cliquet manquait
///
/// `CliquetFixturePassageTest` compte les semeurs de **passage**. Un test qui s'arrête au point d'écoute
/// n'y figure donc pas, par construction : treize fichiers semaient la topologie à la main sans avoir
/// jamais été comptés.
///
/// C'était invisible pour une raison précise : `JeuDeDonneesPassage.semer()` imposait un passage, une
/// session et un enregistrement original. Les tests qui n'avaient besoin que d'un point ne pouvaient pas
/// l'utiliser, alors ils semaient à la main - et rien ne le signalait. La dette était **hors du champ de
/// la mesure**, pas absente.
///
/// L'entrée légère `semerSiteEtPoint()` lève le frein ; ce cliquet rend visible ce qu'il reste à migrer.
///
/// ## Ce qui est hors mesure, et pourquoi c'est écrit
///
/// `PointDaoTest` et `PointCommuneDaoTest` testent les **DAO** de point. Créer des points à la main est
/// leur objet même : leur donner une fixture reviendrait à tester la fixture. Même raison que le test du
/// parseur WAV dans [CliquetWriterWavTest].
class CliquetSemisTopologieTest {

    /// La dette épinglée : **elle est vide**, et le cliquet reste pour empêcher qu'elle renaisse.
    ///
    /// Les deux derniers ont attendu que la fixture sache dire ce qu'ils expriment : un point **sans GPS**
    /// et un site **hors protocole** (#2989). Un cliquet ne fait pas que compter - il dit aussi quand
    /// l'outil vers lequel il pointe n'est pas encore à la hauteur.
    private static final List<String> SEMENT_LA_TOPOLOGIE_A_LA_MAIN = List.of();

    @Test
    @DisplayName("La dette des semis de topologie ne peut que rétrécir : aucun nouveau site créé à la main")
    void la_dette_ne_peut_que_retrecir() {
        Cliquet.verifier(
                Cliquet.fichiersOu(CliquetSemisTopologieTest::semeLaTopologieALaMain),
                SEMENT_LA_TOPOLOGIE_A_LA_MAIN,
                "les tests qui sèment un site et un point à la main",
                "fr.univ_amu.iut.fixture.JeuDeDonneesPassage#semerSiteEtPoint",
                "SEMENT_LA_TOPOLOGIE_A_LA_MAIN, dans ce fichier");
    }

    /// Créer un [fr.univ_amu.iut.sites.model.PointDEcoute] **et** le persister : la conjonction distingue
    /// l'écriture d'une simple construction en mémoire, qu'un test de projection peut légitimement faire.
    ///
    /// Un fichier qui va jusqu'au passage relève de l'autre cliquet, pas de celui-ci : les deux dettes se
    /// migrent vers deux entrées différentes de la fixture, et les mélanger rendrait chaque compte
    /// illisible. Cette **partition** est délibérée et reste.
    ///
    /// ⚠️ Ce qui ne restait pas, c'est le **court-circuit** qui l'accompagnait : le détecteur rendait
    /// aussi `false` dès qu'un fichier nommait `JeuDeDonneesPassage`, c'est-à-dire dès qu'il était
    /// **partiellement** migré. Il devenait donc aveugle exactement là où il devait parler, ce que
    /// l'ADR 2867 nomme comme le premier piège du patron - corrigé sur le cliquet des fixtures par #2714,
    /// et réintroduit ici par moi en le posant.
    ///
    /// Deux fichiers étaient masqués : `ServiceImportTest` et `ServiceSoldeSaisonTest`, qui prennent la
    /// fixture pour leur nuit et sèment encore leur topologie à la main. La liste **grandit** de deux, ce
    /// qui est le sens de variation le plus inconfortable et le seul honnête ici.
    private static boolean semeLaTopologieALaMain(Cliquet.Fichier fichier) {
        if (fichier.dansLePaquet("cliquet") || fichier.dansLePaquet("fixture")) {
            return false;
        }
        String nom = fichier.chemin().getFileName().toString();
        if (nom.equals("PointDaoTest.java") || nom.equals("PointCommuneDaoTest.java")) {
            return false;
        }
        String source = fichier.source();
        if (source.contains("new Passage(")) {
            return false;
        }
        return source.contains("new PointDEcoute(")
                && (source.contains("new PointDao(") || source.contains("pointDao"));
    }
}
