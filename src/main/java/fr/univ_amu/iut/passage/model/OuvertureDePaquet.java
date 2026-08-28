package fr.univ_amu.iut.passage.model;

import fr.univ_amu.iut.commun.api.ProfilVigieChiro;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/// Ouvre un paquet d'emport, et **appose l'identité du relecteur à ce moment-là** (#4626, ADR 4517).
///
/// **Pourquoi à l'ouverture et non au jugement.** L'identité locale vit dans `connexion.json` et se
/// lit hors connexion, mais `StockageConnexion.profil()` la rend **vide** au-delà de quatorze jours
/// sans reconnexion. Un relecteur qui ouvre un paquet aujourd'hui et le juge dans trois semaines
/// produirait des verdicts que personne ne peut attribuer. La relever une fois, à l'ouverture, coupe
/// cette dépendance.
///
/// **Pourquoi le pseudo et non l'identifiant.** Ce qui signera un verdict est lu par un humain.
/// L'objectid de la plateforme ne dit rien à personne.
public final class OuvertureDePaquet {

    private OuvertureDePaquet() {}

    /// Ouvre `paquet` au nom de `identite`.
    ///
    /// @param paquet l'archive à ouvrir
    /// @param identite le profil connecté, tel que `StockageConnexion.profil()` le rend
    /// @return le paquet ouvert, signé du pseudo relevé
    /// @throws IllegalStateException si l'identité manque, ou si l'archive ne porte pas de manifeste
    /// @throws IOException si l'archive est illisible
    public static PaquetOuvert ouvrir(Path paquet, Optional<ProfilVigieChiro> identite) throws IOException {
        Objects.requireNonNull(paquet, "paquet");
        String pseudo = identite.map(ProfilVigieChiro::pseudo)
                .orElseThrow(() -> new IllegalStateException(
                        "Ce paquet ne s'ouvre pas sans identité : reconnectez-vous à la plateforme,"
                                + " sinon les verdicts que vous poseriez ne pourraient être attribués"
                                + " à personne."));

        String manifeste = null;
        List<String> sequences = new ArrayList<>();
        try (ZipInputStream flux = new ZipInputStream(Files.newInputStream(paquet))) {
            for (ZipEntry entree = flux.getNextEntry(); entree != null; entree = flux.getNextEntry()) {
                if (PlanDePaquet.NOM_MANIFESTE.equals(entree.getName())) {
                    manifeste = new String(lire(flux), StandardCharsets.UTF_8);
                } else if (entree.getName().startsWith("sequences/")) {
                    sequences.add(entree.getName());
                }
            }
        }
        if (manifeste == null) {
            throw new IllegalStateException("Archive sans " + PlanDePaquet.NOM_MANIFESTE
                    + " : ce n'est pas un paquet d'emport, et rien ne dit de quelle nuit il s'agirait.");
        }
        return new PaquetOuvert(pseudo, manifeste, sequences);
    }

    /// Le contenu de l'entrée courante. `ZipInputStream#readAllBytes` lit bien l'entrée seule, mais
    /// le dire ici évite qu'un lecteur se demande si le flux entier y passe.
    private static byte[] lire(ZipInputStream flux) throws IOException {
        ByteArrayOutputStream tampon = new ByteArrayOutputStream();
        flux.transferTo(tampon);
        return tampon.toByteArray();
    }
}
