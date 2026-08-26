package fr.univ_amu.iut.commun.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Les URL pré-signées que le serveur renvoie décident **où partent les données** (#2734) : le client
/// ne peut pas les suivre sans les regarder.
///
/// La forme de référence n'est pas devinée : `resources/fichiers.py:188` du miroir de l'API construit
/// **toutes** les URL signées en `https://<bucket>.s3.amazonaws.com/<objet>`, schéma codé en dur.
class UrlSigneeAdmiseTest {

    private static final String PROPRIETE = "vigiechiro.s3.hotes";

    private static final String URL_REELLE =
            "https://vigiechiro.s3.amazonaws.com/5f2b?AWSAccessKeyId=AK&Expires=1&Signature=abc";

    @AfterEach
    void nettoyerLaPropriete() {
        System.clearProperty(PROPRIETE);
    }

    @Test
    @DisplayName("L'URL que la plateforme produit vraiment est admise")
    void url_nominale_admise() {
        assertThat(UrlSigneeAdmise.motifDeRefus(URL_REELLE)).isEmpty();
    }

    @Test
    @DisplayName("HTTP en clair : refusé, quel que soit l'hôte")
    void http_en_clair_refuse() {
        Optional<String> motif = UrlSigneeAdmise.motifDeRefus("http://vigiechiro.s3.amazonaws.com/5f2b?sig=abc");

        assertThat(motif).isPresent();
        assertThat(motif.orElseThrow()).contains("https");
    }

    @Test
    @DisplayName("Un autre schéma (file, ftp) : refusé avant tout appel")
    void autre_schema_refuse() {
        assertThat(UrlSigneeAdmise.motifDeRefus("file:///etc/passwd")).isPresent();
        assertThat(UrlSigneeAdmise.motifDeRefus("ftp://ailleurs.example/x")).isPresent();
    }

    @Test
    @DisplayName("Hôte inattendu : refusé, et le refus nomme l'hôte ET le réglage à poser")
    void hote_inattendu_refuse() {
        Optional<String> motif = UrlSigneeAdmise.motifDeRefus("https://ailleurs.example/5f2b?sig=abc");

        assertThat(motif).isPresent();
        // Sans ces deux informations, un changement d'hébergement côté plateforme devient un mur.
        // Et le geste nommé doit être ATTEIGNABLE : le refus citait la propriété JVM, qu'un produit
        // installé ne permet pas de poser (#4075). Il nomme désormais l'option de la ligne de commande.
        assertThat(motif.orElseThrow())
                .contains("ailleurs.example")
                .contains("--reglage s3.hotes=")
                .doesNotContain("-D");
    }

    @Test
    @DisplayName("Boucle locale : refusée, c'est le cas d'école de l'URL détournée")
    void loopback_refuse() {
        assertThat(UrlSigneeAdmise.motifDeRefus("https://127.0.0.1:9000/5f2b")).isPresent();
        assertThat(UrlSigneeAdmise.motifDeRefus("https://localhost/5f2b")).isPresent();
    }

    @Test
    @DisplayName("Un hôte qui se termine par la marque sans en être un sous-domaine est refusé")
    void faux_sous_domaine_refuse() {
        // « evils3.amazonaws.com.attaquant.example » et « moncompte-s3.amazonaws.com.pirate.net » se
        // terminent par autre chose ; « s3.amazonaws.com.pirate.net » finit par le pirate. Le piège que
        // guette un « contains » ou un « endsWith » mal placé.
        assertThat(UrlSigneeAdmise.motifDeRefus("https://s3.amazonaws.com.pirate.net/x"))
                .isPresent();
        assertThat(UrlSigneeAdmise.motifDeRefus("https://faux-s3.amazonaws.com.pirate.net/x"))
                .isPresent();
    }

    @Test
    @DisplayName("URL illisible : refusée plutôt qu'émise au hasard")
    void url_illisible_refusee() {
        assertThat(UrlSigneeAdmise.motifDeRefus("pas une url du tout")).isPresent();
        assertThat(UrlSigneeAdmise.motifDeRefus("")).isPresent();
        assertThat(UrlSigneeAdmise.motifDeRefus(null)).isPresent();
    }

    @Test
    @DisplayName("Un https sans hôte lisible est refusé : le bon schéma ne suffit pas")
    void https_sans_hote_refuse() {
        // « https:5f2b » est une URI valide mais opaque : elle porte le bon schéma et aucun hôte. Sans
        // ce cas, la branche qui le refuse n'était jamais exercée (trou signalé par PIT).
        Optional<String> motif = UrlSigneeAdmise.motifDeRefus("https:5f2b?AWSAccessKeyId=AK");

        assertThat(motif).isPresent();
        assertThat(motif.orElseThrow()).contains("hôte");
    }

    @Test
    @DisplayName("La propriété ouvre l'hôte d'un déploiement de développement (DEV_FAKE_S3_URL)")
    void surcharge_pour_une_instance_de_developpement() {
        // Côté serveur, DEV_FAKE_S3_URL fait renvoyer une URL quelconque (fichiers.py:125). Sans
        // échappatoire, l'application deviendrait inutilisable contre une telle instance.
        System.setProperty(PROPRIETE, "s3.interne.example, autre.example");

        assertThat(UrlSigneeAdmise.motifDeRefus("https://s3.interne.example/5f2b"))
                .isEmpty();
        assertThat(UrlSigneeAdmise.motifDeRefus("https://autre.example/5f2b")).isEmpty();
        assertThat(UrlSigneeAdmise.motifDeRefus("https://encore-ailleurs.example/5f2b"))
                .as("la surcharge remplace la liste, elle ne l'ouvre pas à tout")
                .isPresent();
        assertThat(UrlSigneeAdmise.motifDeRefus("http://s3.interne.example/5f2b"))
                .as("ouvrir un hôte n'ouvre pas le HTTP en clair")
                .isPresent();
    }

    @Test
    @DisplayName("Une virgule en trop dans la surcharge n'ouvre pas la porte à tout")
    void une_entree_vide_de_la_surcharge_n_admet_rien() {
        // Trouvé par mutation (clôture du chantier #2720) : le `filter(hote -> !hote.isEmpty())` de
        // `hotesAdmis` survivait à sa suppression, faute de test. Une entrée vide n'est pas inerte -
        // la comparaison admet un hôte qui « se termine par . + admis », donc par « . » tout court.
        //
        // Un nom de domaine pleinement qualifié PEUT se terminer par un point (`exemple.com.` est un
        // FQDN valide, et `URI.getHost()` le rend tel quel). Sans le filtre, une virgule de trop dans
        // la propriété suffirait donc à admettre n'importe quel hôte écrit sous cette forme.
        System.setProperty(PROPRIETE, "s3.interne.example,,autre.example");

        assertThat(UrlSigneeAdmise.motifDeRefus("https://s3.interne.example/5f2b"))
                .as("les entrées réelles restent admises malgré la virgule en trop")
                .isEmpty();
        assertThat(UrlSigneeAdmise.motifDeRefus("https://attaquant.example./5f2b"))
                .as("un FQDN à point final ne doit pas être admis par une entrée vide")
                .isPresent();
    }
}
