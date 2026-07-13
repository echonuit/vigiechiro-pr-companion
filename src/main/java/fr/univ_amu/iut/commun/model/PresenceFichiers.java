package fr.univ_amu.iut.commun.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/// Présence sur disque d'un lot de fichiers persistés, évaluée en balayage **groupé** : un seul
/// accès disque par **dossier parent** (listage des noms), jamais un `Files.exists` par fichier.
/// Sur un passage réel (des milliers de séquences dans un même `transformes/`), le coût est celui
/// d'un listage de dossier, pas de milliers de `stat`.
///
/// Noyau partagé entre l'audit de cohérence (`ServiceAuditCoherence`) et la disponibilité de
/// l'audio d'un passage (`ServiceDisponibiliteAudio`, #1298) : la logique « présent / absent /
/// hors workspace » vit ici et nulle part ailleurs. La nuance [Presence#EXTERNE_INTROUVABLE]
/// distingue un fichier hors workspace (carte SD peut-être non montée) d'un fichier du workspace
/// réellement absent.
public final class PresenceFichiers {

    /// Verdict de présence d'un chemin persisté.
    public enum Presence {
        /// Le fichier existe sur disque.
        PRESENTE,
        /// Le fichier, attendu sous le workspace, est absent du disque.
        ABSENTE,
        /// Le fichier, hors workspace (carte SD, disque externe), est introuvable : le média n'est
        /// peut-être simplement pas monté.
        EXTERNE_INTROUVABLE
    }

    /// Accès disque unitaire du balayage : les noms des entrées présentes dans un dossier.
    /// Injectable en test pour compter les accès (garantie « un accès par dossier, pas par
    /// fichier »).
    @FunctionalInterface
    public interface Balayeur {
        /// Noms simples des entrées de `dossier` ; ensemble vide si le dossier est absent ou
        /// illisible.
        Set<String> nomsPresents(Path dossier);
    }

    private final Workspace workspace;
    private final Balayeur balayeur;

    public PresenceFichiers(Workspace workspace) {
        this(workspace, PresenceFichiers::listerNoms);
    }

    /// Variante à [Balayeur] injecté (tests : compteur d'accès disque).
    public PresenceFichiers(Workspace workspace, Balayeur balayeur) {
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.balayeur = Objects.requireNonNull(balayeur, "balayeur");
    }

    /// Évalue la présence de chaque chemin de `chemins`. Les clés du résultat sont les chaînes
    /// reçues, telles quelles ; les chemins `null` ou blancs sont ignorés (absents du résultat).
    /// Un seul balayage par dossier parent, quel que soit le nombre de fichiers dedans.
    public Map<String, Presence> evaluer(Collection<String> chemins) {
        Map<Path, List<String>> parDossier = new HashMap<>();
        for (String chemin : chemins) {
            if (chemin == null || chemin.isBlank()) {
                continue;
            }
            // getParent() n'est nul que pour une racine du système de fichiers : un « fichier »
            // persisté de ce genre est pathologique, il sera classé absent (balayage vide).
            parDossier
                    .computeIfAbsent(normaliser(chemin).getParent(), dossier -> new ArrayList<>())
                    .add(chemin);
        }
        Map<String, Presence> presences = new HashMap<>();
        for (Map.Entry<Path, List<String>> entree : parDossier.entrySet()) {
            Set<String> noms = entree.getKey() == null ? Set.of() : balayeur.nomsPresents(entree.getKey());
            for (String chemin : entree.getValue()) {
                presences.put(chemin, verdict(normaliser(chemin), noms));
            }
        }
        return presences;
    }

    private Presence verdict(Path fichier, Set<String> nomsPresents) {
        Path nom = fichier.getFileName();
        if (nom != null && nomsPresents.contains(nom.toString())) {
            return Presence.PRESENTE;
        }
        return fichier.startsWith(workspace.racine()) ? Presence.ABSENTE : Presence.EXTERNE_INTROUVABLE;
    }

    private static Path normaliser(String chemin) {
        return Path.of(chemin).toAbsolutePath().normalize();
    }

    /// Balayage réel : listage du dossier via `Files.list`. Dossier absent ou illisible = aucun
    /// nom (les fichiers attendus dedans seront classés absents ou introuvables).
    private static Set<String> listerNoms(Path dossier) {
        if (!Files.isDirectory(dossier)) {
            return Set.of();
        }
        try (Stream<Path> enfants = Files.list(dossier)) {
            return enfants.map(enfant -> enfant.getFileName().toString()).collect(Collectors.toUnmodifiableSet());
        } catch (IOException e) {
            return Set.of();
        }
    }
}
