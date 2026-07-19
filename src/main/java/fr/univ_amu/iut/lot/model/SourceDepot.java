package fr.univ_amu.iut.lot.model;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// Ce que le moteur de depot doit televerser, et **comment obtenir chaque fichier au moment de
/// l'envoyer** (#1993).
///
/// Le moteur consommait auparavant une `List<Path>` construite d'avance : toutes les archives
/// devaient donc **exister** avant que la premiere ne parte. Or les identifiants du plan sont
/// connus bien plus tot que les fichiers eux-memes - [PlanificateurArchives] nomme les archives
/// `prefixe-N.zip` de facon deterministe **avant toute ecriture**. Separer « ce qu'il y a a
/// deposer » de « ou se trouve le fichier » est ce qui permettra de generer au fil de l'eau
/// (#1995) et de regenerer a la reprise ce qui a ete libere (#1994).
///
/// Une source expose donc trois choses : les identifiants (connus tot), la resolution d'un fichier
/// (tardive, et faillible), et l'[EmpreinteLot] de sa liste source (pour detecter qu'un lot a
/// change entre deux reprises).
public interface SourceDepot {

    /// Identifiants des unites a deposer, dans l'ordre, tels qu'ils nommeront les lignes du plan.
    /// Disponibles **sans que les fichiers correspondants existent**.
    List<String> identifiants();

    /// Le fichier a televerser pour cet identifiant, resolu au moment de l'envoi.
    ///
    /// `Optional.empty()` signale une indisponibilite que l'appelant doit traiter : aujourd'hui un
    /// echec d'unite (« fichier introuvable »), demain une regeneration (#1994).
    Optional<Path> resoudre(String identifiant);

    /// Empreinte de la liste source, persistee avec le plan et recalculee a la reprise.
    ///
    /// **Calculee a l'appel, pas a la construction** : elle lit la taille des fichiers, donc elle
    /// exige qu'ils existent. Une source peut etre construite bien avant que ce soit le cas (c'est
    /// tout l'interet de separer les identifiants des fichiers) ; seul le moment ou l'on pose le plan
    /// se trouve apres l'ecriture.
    String empreinte();

    /// Source adossee a des fichiers **deja presents** sur le disque : le comportement d'origine.
    ///
    /// Les identifiants sont les noms de fichiers, la resolution est immediate, et l'empreinte porte
    /// sur les fichiers dans l'ordre donne.
    static SourceDepot desFichiers(List<Path> fichiers) {
        Map<String, Path> parIdentifiant = new LinkedHashMap<>();
        for (Path fichier : fichiers) {
            parIdentifiant.put(fichier.getFileName().toString(), fichier);
        }
        return new SourceFichiers(parIdentifiant);
    }

    /// Implantation de [SourceDepot#desFichiers] : une vue immuable sur des fichiers deja ecrits.
    ///
    /// La copie defensive passe par un [LinkedHashMap] et non `Map.copyOf` : l'ordre des unites est
    /// significatif (il fixe l'ordre du plan et celui de l'empreinte), or `Map.copyOf` ne le
    /// conserve pas.
    record SourceFichiers(Map<String, Path> parIdentifiant) implements SourceDepot {

        public SourceFichiers {
            parIdentifiant = Collections.unmodifiableMap(new LinkedHashMap<>(parIdentifiant));
        }

        @Override
        public List<String> identifiants() {
            return List.copyOf(parIdentifiant.keySet());
        }

        @Override
        public Optional<Path> resoudre(String identifiant) {
            return Optional.ofNullable(parIdentifiant.get(identifiant));
        }

        @Override
        public String empreinte() {
            return EmpreinteLot.de(new ArrayList<>(parIdentifiant.values()));
        }
    }
}
