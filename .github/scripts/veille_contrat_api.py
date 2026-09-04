#!/usr/bin/env python3
"""Depuis combien de temps le contrat API n a-t-il pas REELLEMENT tourne ? (#2748, porté du bash).

## Le defaut corrige

`api-live.yml` reste vert quand le jeton est expire ou la plateforme injoignable : c est voulu, un
jeton VigieChiro vit 14 jours face a un passage hebdomadaire, il expire donc regulierement et un
rouge permanent ne signalerait plus rien. Mais la consequence est qu un passage vert ne distingue pas
« contrat verifie » de « contrat saute », et qu une longue periode sans verification reelle est
invisible. Mesure au triage : deux passages verts d affilee n avaient rien verifie, la derniere
execution reelle remontant a 16 jours. Personne ne l avait vu, et c est le point : il n y avait rien
a voir.

## Ce qu il fait, et ce qu il ne stocke pas

Il ne persiste RIEN. L historique des passages EST la date recherchee. Un fichier commite, un
artefact (90 jours) ou un cache (7 jours) deviendraient chacun une seconde chose a surveiller, dont
la premiere panne serait, ici encore, un silence.

Entree : sur l entree standard, une ligne par etape de l historique, en TSV

    dateISO<TAB>nom de l etape<TAB>conclusion

## Il refuse de conclure quand c est LUI qui est casse

Le detecteur cherche l etape par son NOM, une chaine ecrite dans `api-live.yml`. La renommer
casserait la detection en silence, ce qui refabriquerait exactement le defaut corrige ici. Trois
refus explicites, donc, plutot qu un « 0 jour » rassurant : historique vide, nom introuvable dans
tout l historique, et aucune execution reelle. Une mesure vide n est pas un zero.

Usage : ... | python3 .github/scripts/veille_contrat_api.py [--jours-max N] [--joue-maintenant]
                                                            [--aujourdhui <ISO>] [--auto-test]
"""

from __future__ import annotations

import datetime as dt
import re
import sys

# Le nom EXACT de l etape dans `api-live.yml`. Seul endroit ou il est ecrit hors du workflow.
ETAPE_CONTRAT = "Contrat API (lecture seule)"

# 21 jours = trois passages hebdomadaires manques. En deca, c est la vie normale d un jeton de 14
# jours ; au-dela, plus personne ne le renouvelle et le contrat n est plus verifie du tout.
JOURS_MAX = 21

# Une execution REELLE, c est une etape qui a tourne. `failure` en fait partie : le contrat a bien
# ete exerce, il a trouve une derive. `skipped` et `cancelled`, non.
REELLE = re.compile(r"^(success|failure)$")


def horodatage(texte: str) -> int | None:
    """L instant en secondes, ou None si la date est illisible."""
    try:
        # `fromisoformat` lit le « Z » depuis 3.11, et le projet exige 3.12 : pas de repli a ecrire.
        instant = dt.datetime.fromisoformat(texte)
    except ValueError:
        return None
    if instant.tzinfo is None:
        instant = instant.replace(tzinfo=dt.UTC)
    return int(instant.timestamp())


def juger(historique: str, jours_max: int, joue_maintenant: bool, maintenant: int) -> int:
    """L age de la derniere execution reelle, ou un refus motive."""
    if not historique.strip():
        print("❌ Historique vide : aucun passage examiné.")
        print(
            "   Ce n'est pas « le contrat n'a jamais tourné », c'est « la question n'a pas été posée »."
        )
        print(
            "   Regarder l'appel à l'API GitHub dans api-live.yml (droit actions:read ? workflow renommé ?)."
        )
        return 1

    lignes_etape = [
        l
        for l in historique.splitlines()
        if len(l.split("\t")) >= 3 and l.split("\t")[1] == ETAPE_CONTRAT
    ]

    if not lignes_etape:
        print(f"❌ L'étape « {ETAPE_CONTRAT} » est introuvable dans tout l'historique examiné.")
        print("   C'est le DÉTECTEUR qui est en cause, pas la fraîcheur du contrat : l'étape a")
        print("   probablement été renommée dans api-live.yml. Reporter le nom dans ETAPE_CONTRAT")
        print("   (veille_contrat_api.py), sinon cette veille se tait pour toujours.")
        return 1

    reelles = [l.split("\t")[0] for l in lignes_etape if REELLE.match(l.split("\t")[2])]
    derniere = max(reelles) if reelles else ""

    if joue_maintenant:
        print("✔ Contrat API joué à l'instant : la vérification est fraîche du jour.")
        if derniere:
            print(f"  (précédente exécution réelle : {derniere})")
        return 0

    if not derniere:
        print(
            f"❌ Aucune exécution réelle du contrat dans les {len(lignes_etape)} derniers passages : tous sautés."
        )
        print("   Les passages étaient verts et n'ont rien vérifié. Renouveler le jeton :")
        print('   gh secret set VIGIECHIRO_TOKEN --repo "$GITHUB_REPOSITORY"  (validité 14 jours)')
        return 1

    age_jours = (maintenant - horodatage(derniere)) // 86400

    if age_jours > jours_max:
        print(
            f"❌ Le contrat API n'a plus été vérifié depuis {age_jours} jours (dernière fois : {derniere})."
        )
        print(
            f"   Seuil : {jours_max} jours, soit {jours_max // 7} passage(s) hebdomadaire(s) manqué(s)."
        )
        print("   Les passages d'ici là étaient verts sans rien vérifier. Renouveler le jeton :")
        print('   gh secret set VIGIECHIRO_TOKEN --repo "$GITHUB_REPOSITORY"  (validité 14 jours)')
        return 1

    print(
        f"✔ Dernière vérification réelle du contrat : {derniere}, il y a {age_jours} jour(s) "
        f"(seuil : {jours_max})."
    )
    return 0


MAINTENANT_TEST = horodatage("2026-08-06T12:00:00Z")


def ligne(jours: int, etape: str, conclusion: str) -> str:
    """Une ligne d historique, datee relativement au « maintenant » du test."""
    instant = dt.datetime.fromtimestamp(MAINTENANT_TEST - jours * 86400, dt.UTC)
    return f"{instant.strftime('%Y-%m-%dT%H:%M:%SZ')}\t{etape}\t{conclusion}"


def _auto_test() -> int:
    """Neuf cas, dont cinq rouges verifies sur leur MESSAGE.

    Le message, et non le seul code de sortie : un `exit 1` peut venir d une erreur du script
    lui-meme, et c est deja arrive dans ce depot de lire un plantage comme une detection reussie.
    """
    import contextlib
    import io

    echecs = 0

    def verifier(
        nom: str, historique: str, attendu: str, fragment: str, joue: bool = False
    ) -> None:
        nonlocal echecs
        tampon = io.StringIO()
        with contextlib.redirect_stdout(tampon):
            code = juger(historique, JOURS_MAX, joue, MAINTENANT_TEST)
        sortie = tampon.getvalue()
        obtenu = "rouge" if code != 0 else "vert"
        if obtenu != attendu:
            print(f"❌ autotest « {nom} » : attendu {attendu}, obtenu {obtenu}")
            for l in sortie.splitlines():
                print(f"      {l}")
            echecs += 1
            return
        if fragment not in sortie:
            print(
                f"❌ autotest « {nom} » : {obtenu} attendu et obtenu, mais le message ne dit pas « {fragment} »"
            )
            for l in sortie.splitlines():
                print(f"      {l}")
            echecs += 1

    frais = ligne(3, ETAPE_CONTRAT, "success") + "\n" + ligne(10, ETAPE_CONTRAT, "skipped")
    verifier("joué il y a 3 jours", frais, "vert", "il y a 3 jour(s)")

    ancien_25 = "\n".join(
        [
            ligne(25, ETAPE_CONTRAT, "success"),
            ligne(4, ETAPE_CONTRAT, "skipped"),
            ligne(11, ETAPE_CONTRAT, "skipped"),
        ]
    )
    verifier("25 jours sans vérification", ancien_25, "rouge", "depuis 25 jours")

    # Le seuil se verifie des DEUX cotes : une borne n est fiable que si on l a vue basculer.
    verifier(
        "21 jours pile, sous le seuil",
        ligne(21, ETAPE_CONTRAT, "success"),
        "vert",
        "il y a 21 jour(s)",
    )
    verifier(
        "22 jours, au-delà du seuil",
        ligne(22, ETAPE_CONTRAT, "success"),
        "rouge",
        "depuis 22 jours",
    )

    # Un contrat qui a tourne et trouve une derive A verifie quelque chose.
    echec_recent = ligne(2, ETAPE_CONTRAT, "failure") + "\n" + ligne(30, ETAPE_CONTRAT, "success")
    verifier("échec récent = vérification réelle", echec_recent, "vert", "il y a 2 jour(s)")

    tous_sautes = ligne(3, ETAPE_CONTRAT, "skipped") + "\n" + ligne(10, ETAPE_CONTRAT, "skipped")
    verifier("tous les passages sautés", tous_sautes, "rouge", "tous sautés")

    # Le cas qui empeche cette veille de devenir, a son tour, un silence.
    autre_nom = (
        ligne(3, "Contrat API renommé entre-temps", "success")
        + "\n"
        + ligne(3, "Set up JDK 25", "success")
    )
    verifier("étape renommée", autre_nom, "rouge", "DÉTECTEUR")

    verifier("historique vide", "", "rouge", "Historique vide")

    # Fraichement joue : c est vrai meme quand l historique, lui, est vieux.
    verifier("joué à l'instant", ancien_25, "vert", "joué à l'instant", True)

    if echecs > 0:
        print(f"Autotest de la veille : {echecs} échec(s).")
        return 1
    print("Autotest de la veille : OK (9 cas, dont 5 rouges vérifiés sur leur message).")
    return 0


if __name__ == "__main__":
    jours_max = JOURS_MAX
    joue_maintenant = False
    aujourdhui = ""
    arguments = sys.argv[1:]
    while arguments:
        if arguments[0] == "--auto-test":
            sys.exit(_auto_test())
        if arguments[0] == "--jours-max":
            jours_max = int(arguments[1])
            arguments = arguments[2:]
            continue
        if arguments[0] == "--joue-maintenant":
            joue_maintenant = True
            arguments = arguments[1:]
            continue
        if arguments[0] == "--aujourdhui":
            aujourdhui = arguments[1]
            arguments = arguments[2:]
            continue
        print(f"option inconnue : {arguments[0]}", file=sys.stderr)
        sys.exit(2)

    maintenant = horodatage(aujourdhui) if aujourdhui else int(dt.datetime.now(dt.UTC).timestamp())
    sys.exit(juger(sys.stdin.read(), jours_max, joue_maintenant, maintenant))
