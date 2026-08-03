package fr.univ_amu.iut.commun.persistence;

import java.util.Objects;

/// Une racine de session **telle que la sauvegarde complète l'a emportée** (#2726).
///
/// Le `cheminOrigine` est ce que la sauvegarde ne savait pas conserver jusqu'ici : sans lui, une
/// restauration ne peut ni remettre les dossiers où ils étaient, ni corriger les `root_path` de la
/// base, et la promesse « la restauration remet la base et les dossiers de son » ne tient que si
/// l'on restaure sur la machine d'origine.
///
/// L'`identifiant` est **le nom du dossier** sous `sessions/`. Il est unique par construction :
/// deux racines homonymes sur deux disques (`/mnt/a/Nuit-01` et `/mnt/b/Nuit-01`) visaient la même
/// destination et fusionnaient en silence.
///
/// @param identifiant nom du dossier sous `sessions/`, lisible et unique
/// @param cheminOrigine chemin absolu d'où la racine a été copiée
/// @param fichiers nombre de fichiers copiés
/// @param octets somme de leurs tailles
/// @param empreinte empreinte de l'inventaire, cf. [InventaireDossier]
public record RacineSauvegardee(String identifiant, String cheminOrigine, int fichiers, long octets, String empreinte) {

    public RacineSauvegardee {
        Objects.requireNonNull(identifiant, "identifiant");
        Objects.requireNonNull(cheminOrigine, "cheminOrigine");
        Objects.requireNonNull(empreinte, "empreinte");
    }

    static RacineSauvegardee de(String identifiant, String cheminOrigine, InventaireDossier inventaire) {
        return new RacineSauvegardee(
                identifiant, cheminOrigine, inventaire.fichiers(), inventaire.octets(), inventaire.empreinte());
    }
}
