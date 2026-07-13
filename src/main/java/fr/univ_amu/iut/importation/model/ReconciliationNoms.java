package fr.univ_amu.iut.importation.model;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Résolution des **collisions de noms** entre tranches, puis déplacement des fichiers du dossier temporaire
/// vers `transformes/`.
///
/// ## Pourquoi des collisions ?
///
/// Le nommage est **horodaté** : la tranche d'index k d'un original porte l'heure `début + 5k s` (+ `_000`).
/// Or les enregistrements peuvent se **chevaucher sur la grille de 5 s** : la tranche de queue d'un long
/// (ex. `…_205332` de 10 s → tranche `…_205342_000`) tombe sur l'heure de début d'un enregistrement plus
/// récent (`…_205342`, dont la tête veut aussi `…_205342_000`). Deux tranches veulent le même nom.
///
/// ## Règle (validée sur les données réelles Car640380)
///
/// Le **plus ancien enregistrement l'emporte** : il garde le `_000` (c'est ce que porte l'`observations.csv`,
/// donc la jointure observation ↔ audio reste correcte). Le perdant n'est **pas perdu** : il est renommé en
/// `_001` (puis `_002`…), disponible à l'écoute mais sans observation associée. Le traitement dans l'ordre
/// chronologique (noms de fichiers horodatés triés) rend l'attribution **déterministe** (R11).
final class ReconciliationNoms {

    /// Suffixe de séquence `_NNN` juste avant l'extension (à incrémenter en cas de collision).
    private static final Pattern SUFFIXE_SEQUENCE = Pattern.compile("_(\\d{3})(\\.[^.]+)$");

    private ReconciliationNoms() {}

    /// Réconcilie les noms des tranches de `resultats` (écrites en dossiers temporaires) et déplace les
    /// fichiers vers `dossierFinal` avec leur nom définitif. Rend la liste dans l'**ordre d'origine** (les
    /// rejets sont conservés tels quels).
    static List<ResultatDecoupage> reconcilier(List<ResultatDecoupage> resultats, Path dossierFinal) {
        // Ordre chronologique via le nom de fichier horodaté : le plus ancien réserve ses noms en premier.
        List<ResultatDecoupage> chronologique = resultats.stream()
                .filter(ResultatDecoupage::reussi)
                .sorted(Comparator.comparing(r -> r.original().getFileName().toString()))
                .toList();

        Set<String> nomsPris = new HashSet<>();
        Map<Path, TransformationOriginal> reconciliees = new HashMap<>();
        for (ResultatDecoupage resultat : chronologique) {
            TransformationOriginal origine = resultat.transformation();
            List<SequenceProduite> tranches = new ArrayList<>();
            for (SequenceProduite sequence : origine.sequences()) {
                String nomFinal = premierNomLibre(sequence.nomFichier(), nomsPris);
                Path cheminFinal = deplacer(sequence.chemin(), dossierFinal.resolve(nomFinal));
                // Le déplacement ne change pas le contenu : taille et empreinte (#1299) suivent telles
                // quelles, seuls le nom et le chemin sont réconciliés.
                tranches.add(new SequenceProduite(
                        sequence.index(),
                        nomFinal,
                        cheminFinal,
                        sequence.frequenceSortieHz(),
                        sequence.dureeSecondes(),
                        sequence.offsetSourceSecondes(),
                        sequence.octets(),
                        sequence.empreinte()));
            }
            reconciliees.put(
                    resultat.original(),
                    new TransformationOriginal(
                            origine.nomOriginal(),
                            origine.cheminOriginal(),
                            origine.frequenceSourceHz(),
                            origine.frequenceSortieHz(),
                            origine.dureeSourceSecondes(),
                            origine.sha256(),
                            origine.tailleSourceOctets(),
                            tranches));
        }

        return resultats.stream()
                .map(resultat -> reconciliees.containsKey(resultat.original())
                        ? new ResultatDecoupage(resultat.original(), reconciliees.get(resultat.original()), null)
                        : resultat)
                .toList();
    }

    /// Premier nom libre à partir de `nom` : `nom` s'il est disponible, sinon son suffixe `_000` incrémenté
    /// en `_001`, `_002`… jusqu'à un nom non pris. Le nom retenu est marqué pris.
    private static String premierNomLibre(String nom, Set<String> pris) {
        String candidat = nom;
        int index = 0;
        while (!pris.add(candidat)) {
            index++;
            candidat = avecIndexSequence(nom, index);
        }
        return candidat;
    }

    /// Remplace le suffixe `_NNN` final de `nom` par `_index` (3 chiffres). Ex. `…_205342_000.wav`, index 1
    /// → `…_205342_001.wav`.
    private static String avecIndexSequence(String nom, int index) {
        String suffixe = String.format(Locale.ROOT, "_%03d", index);
        Matcher m = SUFFIXE_SEQUENCE.matcher(nom);
        if (m.find()) {
            return nom.substring(0, m.start()) + suffixe + m.group(2);
        }
        return nom + suffixe;
    }

    private static Path deplacer(Path source, Path cible) {
        try {
            Files.createDirectories(cible.getParent());
            // REPLACE_EXISTING : réimport / reprise (#231) — le dossier de session est réutilisé (clé =
            // quadruplet), donc une tentative antérieure a pu laisser une tranche du même nom. On la
            // réécrit (R11 : sortie déterministe), comme le faisait l'écriture directe historique.
            return Files.move(source, cible, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Déplacement de séquence impossible : " + source.getFileName(), e);
        }
    }
}
