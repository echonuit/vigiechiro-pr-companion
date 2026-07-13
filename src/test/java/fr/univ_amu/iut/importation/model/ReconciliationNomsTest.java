package fr.univ_amu.iut.importation.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Résolution des collisions de noms de tranches (nommage horodaté) : le **plus ancien enregistrement**
/// garde le `_000`, le perdant passe en `_001` (aucune donnée perdue), les fichiers sont déplacés vers
/// `transformes/`. L'attribution est déterministe (tri chronologique par nom d'original), indépendante de
/// l'ordre d'entrée.
class ReconciliationNomsTest {

    @Test
    @DisplayName("Collision : le plus ancien garde _000, le perdant passe _001 (contenu préservé)")
    void collision_plus_ancien_garde_000(@TempDir Path tmp) throws IOException {
        Path dossierFinal = Files.createDirectories(tmp.resolve("transformes"));
        // Deux originaux dont les tranches visent le MÊME nom horodaté « ..._205342_000 » :
        //  - ancien : enregistrement à 20:53:32, sa 3e tranche (queue) est à +10 s = 20:53:42
        //  - récent : enregistrement à 20:53:42, sa 1re tranche (tête) est à 20:53:42
        String nomCollision = "Car640380-2026-Pass2-Z1-PaRecPR_20260422_205342_000.wav";
        ResultatDecoupage ancien = resultat(tmp, "a", "…_20260422_205332.wav", nomCollision, new byte[] {1, 1});
        ResultatDecoupage recent = resultat(tmp, "b", "…_20260422_205342.wav", nomCollision, new byte[] {2, 2});

        // Ordre d'entrée volontairement inversé (récent d'abord) : la réconciliation trie par nom d'original.
        List<ResultatDecoupage> out = ReconciliationNoms.reconcilier(List.of(recent, ancien), dossierFinal);

        // La liste garde l'ordre d'entrée : index 0 = récent (perdant), index 1 = ancien (gagnant).
        assertThat(out.get(1).transformation().sequences().get(0).nomFichier())
                .isEqualTo("Car640380-2026-Pass2-Z1-PaRecPR_20260422_205342_000.wav");
        assertThat(out.get(0).transformation().sequences().get(0).nomFichier())
                .isEqualTo("Car640380-2026-Pass2-Z1-PaRecPR_20260422_205342_001.wav");

        // Les deux fichiers existent dans transformes/ et leur contenu est préservé (rien perdu).
        Path gagnant = dossierFinal.resolve("Car640380-2026-Pass2-Z1-PaRecPR_20260422_205342_000.wav");
        Path perdant = dossierFinal.resolve("Car640380-2026-Pass2-Z1-PaRecPR_20260422_205342_001.wav");
        assertThat(Files.readAllBytes(gagnant)).containsExactly(1, 1); // l'ancien
        assertThat(Files.readAllBytes(perdant)).containsExactly(2, 2); // le récent
    }

    @Test
    @DisplayName("Sans collision : chaque tranche garde son nom _000 et est déplacée")
    void sans_collision_noms_inchanges(@TempDir Path tmp) throws IOException {
        Path dossierFinal = Files.createDirectories(tmp.resolve("transformes"));
        ResultatDecoupage a =
                resultat(tmp, "a", "rec_20260422_205332.wav", "seq_20260422_205332_000.wav", new byte[] {1});
        ResultatDecoupage b =
                resultat(tmp, "b", "rec_20260422_205342.wav", "seq_20260422_205342_000.wav", new byte[] {2});

        List<ResultatDecoupage> out = ReconciliationNoms.reconcilier(List.of(a, b), dossierFinal);

        assertThat(out.get(0).transformation().sequences().get(0).nomFichier())
                .isEqualTo("seq_20260422_205332_000.wav");
        assertThat(out.get(1).transformation().sequences().get(0).nomFichier())
                .isEqualTo("seq_20260422_205342_000.wav");
        assertThat(Files.exists(dossierFinal.resolve("seq_20260422_205332_000.wav")))
                .isTrue();
        assertThat(Files.exists(dossierFinal.resolve("seq_20260422_205342_000.wav")))
                .isTrue();
    }

    @Test
    @DisplayName("Reprise : une tranche déjà présente (session réutilisée) est réécrite, pas en échec (#231)")
    void reprise_ecrase_la_tranche_existante(@TempDir Path tmp) throws IOException {
        Path dossierFinal = Files.createDirectories(tmp.resolve("transformes"));
        String nomTranche = "seq_20260422_205332_000.wav";
        // 1re passe : la tranche est déplacée vers transformes/.
        ReconciliationNoms.reconcilier(
                List.of(resultat(tmp, "run1", "rec_20260422_205332.wav", nomTranche, new byte[] {1})), dossierFinal);
        // 2e passe (reprise) : même nom, contenu différent → écrasement déterministe (R11), sans échec.
        List<ResultatDecoupage> out = ReconciliationNoms.reconcilier(
                List.of(resultat(tmp, "run2", "rec_20260422_205332.wav", nomTranche, new byte[] {2})), dossierFinal);

        assertThat(out.get(0).transformation().sequences().get(0).nomFichier()).isEqualTo(nomTranche);
        assertThat(Files.readAllBytes(dossierFinal.resolve(nomTranche))).containsExactly(2);
    }

    /// Construit un [ResultatDecoupage] réussi à une seule tranche, dont le fichier (contenu `pcm`) est écrit
    /// dans un sous-dossier temporaire `sousDossier` (comme le ferait le découpage avant réconciliation).
    private static ResultatDecoupage resultat(
            Path tmp, String sousDossier, String nomOriginal, String nomTranche, byte[] pcm) throws IOException {
        Path dossier = Files.createDirectories(tmp.resolve(sousDossier));
        Path fichier = Files.write(dossier.resolve(nomTranche), pcm);
        Path original = Path.of(nomOriginal);
        SequenceProduite tranche =
                new SequenceProduite(0, nomTranche, fichier, 38_400, 5.0, 0.0, pcm.length, "empreinte-" + sousDossier);
        TransformationOriginal transformation = new TransformationOriginal(
                nomOriginal, original, 384_000, 38_400, 5.0, "sha-" + sousDossier, pcm.length, List.of(tranche));
        return new ResultatDecoupage(original, transformation, null);
    }
}
