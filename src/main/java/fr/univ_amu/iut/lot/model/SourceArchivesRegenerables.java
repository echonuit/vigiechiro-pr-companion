package fr.univ_amu.iut.lot.model;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// [SourceDepot] des **archives ZIP**, capable de **regenerer a la demande** une archive absente du
/// disque (#1994).
///
/// ## Le defaut que cette classe ferme
///
/// Jusqu'ici, le moteur recevait la liste des ZIP **presents** sur le disque. Si les archives avaient
/// disparu (liberation d'espace, nettoyage manuel, interruption), `ServiceLot.fichiersDepotParDefaut`
/// n'en trouvait aucune et **basculait sur les sequences WAV**. Le plan de depot changeait alors
/// entierement d'identifiants, et `synchroniserPlan` supprimait les unites ZIP du suivi : la
/// progression partielle etait **jetee en silence**, et le depot repartait de zero dans un autre mode.
///
/// Ici, les identifiants viennent de la **partition** ([CompacteurDepot#planifier]), pas du contenu du
/// dossier : ils sont donc les memes qu'une archive soit sur le disque ou non. Une archive absente est
/// **reproduite** au moment ou on en a besoin, et le plan reste stable.
///
/// ## Pourquoi c'est sur
///
/// La partition est un glouton a ordre preserve : a liste source inchangee, l'archive `N` regeneree est
/// identique a celle qui avait ete produite. Cette condition n'est pas supposee, elle est **verifiee** :
/// l'[EmpreinteLot] posee avec le plan (#1993) est comparee par [DepotVigieChiro] avant toute reprise,
/// et un lot qui a change fait echouer le depot explicitement plutot que de re-televerser une archive
/// de meme nom au contenu different.
public final class SourceArchivesRegenerables implements SourceDepot {

    /// Extension des archives de depot, en facteur : elle sert a la fois a nommer et a relire un rang.
    private static final String EXTENSION = ".zip";

    private final List<Path> sequences;
    private final List<List<Path>> lots;
    private final String prefixe;
    private final Path dossierDepot;
    private final CompacteurDepot compacteur;

    /// @param sequences les sequences source du lot, **dans l'ordre** (l'ordre fixe la partition)
    /// @param prefixe prefixe des archives (nom du dossier de session, R22)
    /// @param dossierDepot dossier `depot/` ou les archives sont ecrites
    /// @param compacteur compacteur configure avec le plafond courant
    public SourceArchivesRegenerables(
            List<Path> sequences, String prefixe, Path dossierDepot, CompacteurDepot compacteur) {
        this.sequences = List.copyOf(Objects.requireNonNull(sequences, "sequences"));
        this.prefixe = Objects.requireNonNull(prefixe, "prefixe");
        this.dossierDepot = Objects.requireNonNull(dossierDepot, "dossierDepot");
        this.compacteur = Objects.requireNonNull(compacteur, "compacteur");
        this.lots = compacteur.planifier(this.sequences);
    }

    @Override
    public List<String> identifiants() {
        List<String> identifiants = new ArrayList<>(lots.size());
        for (int i = 0; i < lots.size(); i++) {
            identifiants.add(nomArchive(i + 1));
        }
        return identifiants;
    }

    /// L'archive demandee : celle du disque si elle s'y trouve, sinon **regeneree** a l'identique.
    ///
    /// `Optional.empty()` seulement si l'identifiant ne designe aucune archive du plan : ce n'est alors
    /// pas une archive manquante mais une unite qui n'appartient pas a ce lot, et le moteur en fait un
    /// echec d'unite plutot que de fabriquer quelque chose au hasard.
    @Override
    public Optional<Path> resoudre(String identifiant) {
        int numero = numeroDe(identifiant);
        if (numero < 1 || numero > lots.size()) {
            return Optional.empty();
        }
        Path archive = dossierDepot.resolve(nomArchive(numero));
        if (Files.isRegularFile(archive)) {
            return Optional.of(archive);
        }
        return Optional.of(compacteur
                .compacterUne(lots.get(numero - 1), prefixe, dossierDepot, numero)
                .chemin());
    }

    /// L'empreinte porte sur les **sequences source**, pas sur les archives : ce sont elles qui
    /// determinent la partition, et elles existent toujours (contrairement aux archives, qu'on libere).
    @Override
    public String empreinte() {
        return EmpreinteLot.de(sequences);
    }

    /// Nombre d'archives que ce lot produira.
    public int nombreArchives() {
        return lots.size();
    }

    private String nomArchive(int numero) {
        return prefixe + "-" + numero + EXTENSION;
    }

    /// Rang de l'archive d'apres son nom (`<prefixe>-N.zip`), ou `-1` si le nom ne suit pas ce motif ou
    /// ne porte pas le prefixe de ce lot.
    private int numeroDe(String identifiant) {
        String attendu = prefixe + "-";
        if (!identifiant.startsWith(attendu) || !identifiant.endsWith(EXTENSION)) {
            return -1;
        }
        String rang = identifiant.substring(attendu.length(), identifiant.length() - EXTENSION.length());
        try {
            return Integer.parseInt(rang);
        } catch (NumberFormatException pasUnRang) {
            return -1;
        }
    }
}
