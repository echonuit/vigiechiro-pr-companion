package fr.univ_amu.iut.cli.commande;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.RapportAncrage;
import fr.univ_amu.iut.passage.model.DecompteAudio;
import fr.univ_amu.iut.passage.model.RapportReactivation;
import fr.univ_amu.iut.passage.model.RapportReactivation.AbsenceReactivation;
import fr.univ_amu.iut.passage.model.VerdictIdentite.NiveauConfiance;
import fr.univ_amu.iut.passage.model.VoieReactivation;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// **La sortie `--json` de `reactiver` compte les perdants de collision** (#5720), sans changer le sens
/// de `manquantes`, qu'un script existant lit toujours comme le total des séquences non revenues.
class ReactiverProjectionTest {

    @Test
    @DisplayName("#5720 : perdantsDeCollision s'ajoute, manquantes garde son total")
    void la_cle_s_ajoute_sans_changer_manquantes() {
        RapportReactivation rapport = new RapportReactivation(
                7,
                0,
                3,
                0,
                NiveauConfiance.CERTITUDE,
                List.of(),
                new DecompteAudio(7, 10),
                VoieReactivation.TRANSFORMES,
                null,
                RapportAncrage.aucun(),
                List.of(new AbsenceReactivation("a_000.wav", "aucun fichier de ce nom dans le dossier", 1)),
                List.of(
                        "Car640380-2026-Pass2-Z1-PaRecPR1925492_20260422_205342_001.wav",
                        "Car640380-2026-Pass2-Z1-PaRecPR1925492_20260422_210015_001.wav"));

        Map<String, Object> projection = Reactiver.projeter(rapport);

        assertThat(projection).containsEntry("perdantsDeCollision", 2).containsEntry("manquantes", 3);
    }
}
