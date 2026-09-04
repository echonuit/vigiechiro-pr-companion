#!/usr/bin/env python3
"""Garde du secret de soumission winget (#2213, porte du bash.)

## Ce qu elle empeche

`winget.yml` refusait de soumettre quand `WINGET_TOKEN` manquait, et sortait **en vert**, avec un
simple `::notice::`. Ce choix etait le bon tant que le workflow se declenchait sur `release:
released`. Il a cesse de l etre quand le workflow est passe en `workflow_dispatch` **seul** : un
dispatch manuel est un geste delibere, et repondre « vert » sans avoir rien soumis annonce une
publication qui n a pas eu lieu.

## Pourquoi elle ne se contente plus de la PRESENCE

Premiere version : « le secret est-il pose ? ». On y avait ecrit, noir sur blanc, qu elle ne
verifierait pas que le jeton FONCTIONNE, au motif que cet echec-la serait de toute facon bruyant.

L experience a dementi ce raisonnement le 2026-08-11. Le jeton etait pose, la garde verte, et la
soumission a echoue sur « Echonuit.VigieChiroCompanion does not exist in microsoft/winget-pkgs ». Ce
message est FAUX : le paquet y etait. Un jeton que `komac` ne peut pas employer rend une reponse vide,
et komac annonce l absence du paquet. L echec etait bruyant, oui, mais il designait le mauvais
coupable - et il coutait un runner Windows, un telechargement de MSI et une installation complete
pour etre atteint. Bruyant ne suffit pas : il faut que le bruit NOMME la cause.

## Les trois controles

1. PRESENCE : le secret est pose et n est pas vide (hors ligne).
2. FORME : il ne porte ni espace ni retour a la ligne autour (hors ligne). Un `\\n` capture au moment
   de poser le secret rend l en-tete `Authorization` invalide, et c est invisible partout ailleurs :
   le jeton « marche » quand on le teste a la main, et pas dans la CI.
3. ACCES : le jeton s authentifie ET voit le paquet (en ligne, `--verifie-l-acces`).

La sonde reseau du controle 3 est **injectable** (`WINGET_SONDE`), ce qui permet a l auto-test de la
jouer hors ligne, dans ses trois issues : jeton muet, jeton authentifie mais aveugle, jeton bon. Sans
cela, ce controle serait le seul du depot a ne pas porter sa preuve.

## Il RAPPORTE la reponse de l API, il ne l avale pas

La premiere version faisait taire l erreur : un 404, un 401, un depassement de quota et un hoquet
reseau y devenaient le meme silence, et la garde en tirait un diagnostic unique qu elle n avait pas
les moyens de porter.

Usage : WINGET_TOKEN=… python3 .github/scripts/verifie_secret_winget.py [--verifie-l-acces]
        python3 .github/scripts/verifie_secret_winget.py --auto-test
"""

from __future__ import annotations

import os
import re
import subprocess
import sys

# Le chemin du paquet dans winget-pkgs, tel que komac le resout.
CHEMIN_PAQUET = "manifests/e/Echonuit/VigieChiroCompanion"
POLITIQUE = re.compile(r"lifetime|enterprise", re.I)


def sonde() -> str:
    return os.environ.get("WINGET_SONDE") or "gh"


def _appelle(jeton: str, *arguments: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [sonde(), *arguments],
        capture_output=True,
        text=True,
        env={**os.environ, "GH_TOKEN": jeton},
        check=False,
    )


def verifier_l_acces(jeton: str) -> int:
    """Controle 3 : le jeton s authentifie ET voit le paquet."""
    identite = _appelle(jeton, "api", "user", "--jq", ".login")
    login = identite.stdout.strip() if identite.returncode == 0 else ""
    if not login:
        print("❌ WINGET_TOKEN est posé, mais il ne s'authentifie pas auprès de GitHub.")
        print()
        print("   Le jeton est expiré, révoqué, ou son contenu n'est pas celui qu'on croit.")
        print("   ⚠️ Un jeton valide COPIÉ AVEC UN RETOUR À LA LIGNE se comporte exactement ainsi :")
        print("   il marche quand on le teste à la main, et pas ici. Le reposer sans :")
        print(
            "     printf '%s' \"$PAT\" | gh secret set WINGET_TOKEN --repo echonuit/vigiechiro-pr-companion"
        )
        return 1

    brut = _appelle(jeton, "api", f"repos/microsoft/winget-pkgs/contents/{CHEMIN_PAQUET}")
    erreur = brut.stderr.strip()
    lu = _appelle(
        jeton, "api", f"repos/microsoft/winget-pkgs/contents/{CHEMIN_PAQUET}", "--jq", ".[].name"
    )
    vu = lu.stdout.strip() if lu.returncode == 0 else ""
    if not vu:
        print(
            f"❌ WINGET_TOKEN s'authentifie (« {login} »), mais l'interrogation du paquet ne rend rien."
        )
        print()
        print(f"   Chemin interrogé : {CHEMIN_PAQUET}")
        print(f"   Réponse de l'API : {erreur or '<vide, sans message d erreur>'}")
        print()
        # Le cas REELLEMENT rencontre, et le seul que personne n avait envisage : ce n est ni un
        # droit manquant ni un jeton mort, c est une politique de l entreprise qui heberge le depot.
        if POLITIQUE.search(erreur):
            print(
                "   ⚠️ C'EST LA POLITIQUE D'ENTREPRISE DE MICROSOFT, PAS UN DÉFAUT DE VOTRE JETON."
            )
            print()
            print(
                "   L'entreprise « Microsoft Open Source », propriétaire de winget-pkgs, refuse tout PAT"
            )
            print(
                "   « classic » dont la DURÉE DE VIE dépasse 8 JOURS. Un jeton parfaitement valide, au bon"
            )
            print("   type et au bon scope, est rejeté sur ce seul critère.")
            print()
            print(
                "   Refaire un PAT « classic », scope public_repo, expiration à 8 JOURS OU MOINS, puis :"
            )
            print(
                "     printf '%s' \"$PAT\" | gh secret set WINGET_TOKEN --repo echonuit/vigiechiro-pr-companion"
            )
            print()
            print(
                "   Ce jeton est donc PÉRISSABLE et le restera. Le geste réaliste est d'en créer un juste"
            )
            print("   avant chaque soumission, qui est de toute façon un geste manuel et rare.")
            return 1

        print(
            "   Lire ce message AVANT de conclure : il sépare des causes que le silence confondait."
        )
        print(
            "     - « lifetime … 8 days » (403) : politique d'entreprise de Microsoft, cf. ci-dessus."
        )
        print(
            "     - « Not Found » (404)      : le jeton n'a pas le droit de lire ce dépôt public."
        )
        print(
            "                                  Un PAT « classic » avec public_repo l'a ; un jeton"
        )
        print(
            "                                  fine-grained restreint à des dépôts choisis ne l'a pas."
        )
        print("     - « Bad credentials » (401): jeton expiré ou révoqué.")
        print("     - « rate limit »           : rien à voir avec les droits, réessayer plus tard.")
        print("     - un message réseau        : ni le jeton ni les droits ne sont en cause.")
        print()
        print(
            "   C'est ce même constat que komac annonce sous la forme trompeuse « does not exist in"
        )
        print("   microsoft/winget-pkgs » : le paquet existe, c'est la lecture qui échoue.")
        return 1

    versions = " ".join(vu.splitlines())
    print(f"Accès winget-pkgs : OK (jeton « {login} », versions vues : {versions}).")
    return 0


def juger(verifie_l_acces: bool = False) -> int:
    """Les trois controles, dans l ordre, et le code de sortie qui va avec."""
    jeton = os.environ.get("WINGET_TOKEN", "")

    # ---- 1. Presence
    if not jeton.strip():
        print("❌ WINGET_TOKEN est absent : aucune soumission ne peut partir vers winget-pkgs.")
        print()
        print(
            "   Ce workflow ne se déclenche qu'à la main. L'avoir lancé veut dire qu'on attend une"
        )
        print(
            "   soumission : sortir en vert sans rien soumettre annoncerait une publication qui n'a pas"
        )
        print("   eu lieu.")
        print()
        print(
            "   Poser le secret (PAT « classic », scope public_repo, un fork echonuit/winget-pkgs) :"
        )
        print(
            "     printf '%s' \"$PAT\" | gh secret set WINGET_TOKEN --repo echonuit/vigiechiro-pr-companion"
        )
        print()
        print("   Détail des prérequis : l'en-tête de .github/workflows/winget.yml.")
        return 1

    # ---- 2. Forme
    if jeton.strip() != jeton:
        print("❌ WINGET_TOKEN porte une espace ou un retour à la ligne autour de sa valeur.")
        print()
        print(
            "   C'est le défaut le plus difficile à voir : le jeton est BON, il s'authentifie quand on le"
        )
        print(
            "   teste à la main, et l'en-tête Authorization qu'il produit ici est invalide. L'échec"
        )
        print("   ressort alors très loin d'ici, sous la forme « le paquet n'existe pas ».")
        print()
        print("   Le reposer sans retour à la ligne (printf, pas echo) :")
        print(
            "     printf '%s' \"$PAT\" | gh secret set WINGET_TOKEN --repo echonuit/vigiechiro-pr-companion"
        )
        return 1

    print("Garde secret winget : OK (WINGET_TOKEN présent, sans espace parasite).")

    # ---- 3. Acces, a la demande
    if verifie_l_acces:
        return verifier_l_acces(jeton)
    return 0


SONDES = {
    "bonne": """#!/usr/bin/env bash
case "$*" in
  *"api user"*) echo "echonuit" ;;
  *contents*)   echo "2.34.2" ;;
esac
""",
    "muette": "#!/usr/bin/env bash\nexit 1\n",
    # L aveugle PARLE : elle rend sur STDERR ce que rend vraiment `gh` sur un 404. C est ce message
    # que la garde doit RAPPORTER, faute de quoi elle diagnostique sans preuve.
    "aveugle": """#!/usr/bin/env bash
case "$*" in
  *"api user"*) echo "echonuit" ;;
  *contents*)   echo "gh: Not Found (HTTP 404)" >&2; exit 1 ;;
esac
""",
    # Le cas REEL du 2026-08-11 : la politique d entreprise de Microsoft.
    "politique": """#!/usr/bin/env bash
case "$*" in
  *"api user"*) echo "nedseb" ;;
  *contents*)   echo "gh: The 'Microsoft Open Source' enterprise forbids access via a personal access tokens (classic) if the token's lifetime is greater than 8 days. (HTTP 403)" >&2; exit 1 ;;
esac
""",
}


def _auto_test() -> int:
    """Quinze cas, dont neuf rouges. Le controle 3 se joue par SONDE, hors ligne."""
    import contextlib
    import io
    import pathlib
    import tempfile

    echecs = 0
    with tempfile.TemporaryDirectory(prefix="vc-winget-") as tmp:
        bac = pathlib.Path(tmp)
        for nom, corps in SONDES.items():
            chemin = bac / f"sonde-{nom}"
            chemin.write_text(corps, encoding="utf-8")
            chemin.chmod(0o755)

        def joue(jeton, sonde_nom, acces=False) -> tuple[int, str]:
            ancien = {k: os.environ.get(k) for k in ("WINGET_TOKEN", "WINGET_SONDE")}
            if jeton is None:
                os.environ.pop("WINGET_TOKEN", None)
            else:
                os.environ["WINGET_TOKEN"] = jeton
            if sonde_nom:
                os.environ["WINGET_SONDE"] = str(bac / f"sonde-{sonde_nom}")
            else:
                os.environ.pop("WINGET_SONDE", None)
            tampon = io.StringIO()
            try:
                with contextlib.redirect_stdout(tampon), contextlib.redirect_stderr(tampon):
                    code = juger(acces)
            finally:
                for cle, valeur in ancien.items():
                    if valeur is None:
                        os.environ.pop(cle, None)
                    else:
                        os.environ[cle] = valeur
            return code, tampon.getvalue()

        def verifie(attendu, libelle, jeton, sonde_nom=None, acces=False) -> str:
            nonlocal echecs
            code, sortie = joue(jeton, sonde_nom, acces)
            if code == attendu:
                print(f"  ✔ {libelle}")
            else:
                print(f"  ✘ {libelle} : attendu {attendu}, obtenu {code}")
                echecs = 1
            return sortie

        # ---- Controle 1 : presence. Le defaut d origine, celui qui sortait en vert.
        verifie(1, "un secret ABSENT de l environnement est refusé", None)
        verifie(1, "un secret VIDE est refusé", "")
        verifie(1, "un secret réduit à des espaces est refusé", "   ")

        # ---- Controle 2 : forme. Le suspect n°1, et le seul invisible a l oeil nu.
        verifie(1, "un secret avec un RETOUR A LA LIGNE final est refusé", "ghp_unJeton\n")
        verifie(1, "un secret avec une espace finale est refusé", "ghp_unJeton ")
        verifie(1, "un secret avec une espace initiale est refusé", " ghp_unJeton")

        # ---- Controles NEGATIFS : la regle doit rester etroite.
        verifie(0, "un secret présent et propre passe", "ghp_unJetonDeTest")
        verifie(0, "un secret présent, d une autre forme, passe aussi", "github_pat_unAutreJeton")

        # ---- Controle 3 : acces, joue par sonde.
        verifie(1, "un jeton qui ne s authentifie pas est refusé", "ghp_x", "muette", True)
        verifie(
            1, "un jeton authentifié mais AVEUGLE au paquet est refusé", "ghp_x", "aveugle", True
        )
        verifie(0, "un jeton qui voit le paquet passe", "ghp_x", "bonne", True)

        # Et le controle d acces ne doit pas se declencher quand on ne le demande pas.
        verifie(0, "sans --verifie-l-acces, aucune sonde n est appelée", "ghp_x", "muette")

        verifie(
            1,
            "un jeton refusé par la politique d entreprise est refusé",
            "ghp_x",
            "politique",
            True,
        )

        _, sortie_politique = joue("ghp_x", "politique", True)
        if "POLITIQUE D'ENTREPRISE" in sortie_politique:
            print(
                "  ✔ la politique d entreprise est NOMMÉE, pas confondue avec un défaut de droits"
            )
        else:
            print("  ✘ la politique d entreprise est NOMMÉE : le message ne la distingue pas")
            for l in sortie_politique.splitlines():
                print(f"      {l}")
            echecs = 1

        # Le cas qui manquait, et son absence a coute un faux diagnostic : la garde doit RAPPORTER la
        # reponse de l API, pas seulement rougir.
        _, sortie_aveugle = joue("ghp_x", "aveugle", True)
        if "HTTP 404" in sortie_aveugle:
            print("  ✔ la réponse de l API est RAPPORTÉE, pas avalée")
        else:
            print(
                "  ✘ la réponse de l API est RAPPORTÉE, pas avalée : « HTTP 404 » absent du message"
            )
            for l in sortie_aveugle.splitlines():
                print(f"      {l}")
            echecs = 1

    if echecs == 0:
        print("Auto-test de la garde secret winget : OK (15 cas, dont 9 rouges).")
    else:
        print(
            "Auto-test de la garde secret winget : ÉCHEC - la règle ne fait plus ce qu'elle promet."
        )
    return echecs


if __name__ == "__main__":
    if "--auto-test" in sys.argv[1:2]:
        sys.exit(_auto_test())
    sys.exit(juger(sys.argv[1:2] == ["--verifie-l-acces"]))
