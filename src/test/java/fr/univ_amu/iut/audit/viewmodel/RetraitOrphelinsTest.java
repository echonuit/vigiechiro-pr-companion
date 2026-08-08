package fr.univ_amu.iut.audit.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.audit.model.BilanNettoyage;
import fr.univ_amu.iut.audit.model.CategorieConstat;
import fr.univ_amu.iut.audit.model.ConstatAudit;
import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.commun.viewmodel.CompteRendu;
import fr.univ_amu.iut.commun.viewmodel.RetourOperation;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// Ce que l'écran demande avant de retirer, et ce qu'il annonce après (#3482).
class RetraitOrphelinsTest {

    private static final String PASS2 = "/w/Car130711-2026-Pass2-Z1";
    private static final String PASS3 = "/w/Car130711-2026-Pass3-Z1";

    private static ConstatAudit orphelin(String chemin) {
        return new ConstatAudit(
                Severite.AVERTISSEMENT, CategorieConstat.DOSSIER_ORPHELIN, null, chemin, "Dossier sans session");
    }

    private static ConstatAudit autreConstat() {
        return new ConstatAudit(Severite.ERREUR, CategorieConstat.DISQUE_MANQUANT, 12L, PASS2 + "/a.wav", "Absent");
    }

    @Nested
    @DisplayName("Ce que l'action ramasse")
    class Perimetre {

        @Test
        @DisplayName("#3482 : seuls les constats « dossier orphelin » désignent un dossier à retirer")
        void seuls_les_orphelins() {
            List<Path> dossiers = RetraitOrphelins.dossiers(List.of(orphelin(PASS2), autreConstat(), orphelin(PASS3)));

            // Le garde-fou qui compte : un « fichier manquant » cite un CHEMIN DE FICHIER dans le même
            // champ `cible`. Élargir le filtre effacerait un dossier de session vivant.
            assertThat(dossiers).containsExactly(Path.of(PASS2), Path.of(PASS3));
        }

        @Test
        @DisplayName("Aucun orphelin : rien à retirer")
        void aucun_orphelin() {
            assertThat(RetraitOrphelins.dossiers(List.of(autreConstat()))).isEmpty();
        }
    }

    @Nested
    @DisplayName("Ce que la confirmation annonce")
    class Confirmation {

        @Test
        @DisplayName("#3482 : elle chiffre le nombre de dossiers et la place regagnée")
        void chiffre_la_perte_et_le_gain() {
            CompteRendu demande =
                    RetraitOrphelins.confirmation(List.of(Path.of(PASS2), Path.of(PASS3)), 3_221_225_472L);

            assertThat(demande.titre()).contains("2");
            assertThat(demande.conclusion()).contains("3,0 Go");
            // On nomme les dossiers : c'est ce qui permet à l'utilisateur de reconnaître une nuit qu'il
            // croyait perdue avant de la supprimer pour de bon.
            assertThat(demande.constats()).hasSize(2);
            assertThat(demande.constats().getFirst().fait()).contains("Car130711-2026-Pass2-Z1");
        }

        @Test
        @DisplayName("Elle dit que le geste est irréversible")
        void dit_que_c_est_irreversible() {
            CompteRendu demande = RetraitOrphelins.confirmation(List.of(Path.of(PASS2)), 1024L);

            assertThat(demande.preambule().toLowerCase()).contains("irréversible");
        }
    }

    @Nested
    @DisplayName("Ce que le compte rendu dit ensuite")
    class CompteRenduFinal {

        @Test
        @DisplayName("Tout est parti : succès, avec la place regagnée")
        void tout_est_parti() {
            RetourOperation retour = RetraitOrphelins.compteRendu(
                    new BilanNettoyage(List.of(Path.of(PASS2), Path.of(PASS3)), List.of(), 3_221_225_472L));

            assertThat(retour.severite()).isEqualTo(Severite.SUCCES);
            assertThat(retour.texte()).contains("2").contains("3,0 Go");
        }

        @Test
        @DisplayName("#3482 : un retrait partiel ne se présente PAS comme un succès")
        void retrait_partiel_n_est_pas_un_succes() {
            RetourOperation retour = RetraitOrphelins.compteRendu(new BilanNettoyage(
                    List.of(Path.of(PASS2)),
                    List.of(new BilanNettoyage.DossierResistant(
                            Path.of(PASS3), "Le processus ne peut pas accéder au fichier")),
                    1024L));

            // C'est le mode de panne de #3448, transposé : annoncer un ménage fait quand un dossier
            // est resté enverrait l'utilisateur croire son disque libéré. Sur Windows, un dossier
            // ouvert dans l'explorateur résiste : ce cas est courant, pas tordu.
            assertThat(retour.severite()).isEqualTo(Severite.AVERTISSEMENT);
            assertThat(retour.texte()).contains("Car130711-2026-Pass3-Z1");
            // Le motif du système remonte jusqu'au bandeau : c'est lui qui dit quoi faire ensuite.
            assertThat(retour.texte()).contains("Le processus ne peut pas accéder au fichier");
        }

        @Test
        @DisplayName("Rien n'est parti : le retour le dit sans crier à l'erreur")
        void rien_n_est_parti() {
            RetourOperation retour = RetraitOrphelins.compteRendu(new BilanNettoyage(List.of(), List.of(), 0L));

            assertThat(retour.texte()).isNotBlank();
            assertThat(retour.severite()).isNotEqualTo(Severite.ERREUR);
        }
    }
}
