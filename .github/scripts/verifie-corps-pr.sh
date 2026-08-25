#!/usr/bin/env bash
# Vérifie la typographie du CORPS d'une pull request.
#
# Le corps de la PR est ce qu'atteint quiconque remonte depuis `git log` : la compétence
# `clore-une-issue` en fait, avec le corps de l'issue, l'un des deux textes qui se relisent dans six
# mois sans le fil. C'est donc de la prose visible au sens de l'article A31.
#
# Cette prose échappait pourtant à toute règle opposable, pour une raison de mécanique : A31
# déclenchait « avant d'être commise », et un corps de PR n'est jamais commis. Rien dans le dépôt ne
# pouvait le refuser. Cf. ADR 4453.
#
# ## Ce qu'elle refuse, et sur quelle mesure
#
# Trois défauts, chacun adossé à une décision déjà prise ailleurs, mesurés sur les dix-huit corps de
# PR du dépôt le 2026-08-25 :
#
# | Défaut | Décision qui le porte | Mesure sur les 18 corps |
# |---|---|---|
# | tiret cadratin de prose | ADR 2843 | 2 lignes, 1 PR, deux vrais défauts |
# | apostrophe courbe | ADR 4368 | 0 |
# | élision sans apostrophe | le garde du TITRE de PR, même règle | 0 |
#
# Zéro sur deux des trois règles : c'est un refus et non un cliquet, la zone étant à zéro le jour de
# la décision. Le cadratin, lui, a deux occurrences dans une PR déjà close, que rien ne peut plus
# retirer de la forge. C'est la démonstration du défaut : ce qui part ne se retire pas.
#
# ## Ce qu'elle ne voit PAS, et c'est assumé
#
# **Les quatre tics rhétoriques** de `CONTRIBUTING.md` - l'antithèse, l'annonce, le tricolon,
# l'aphorisme final. Aucun motif ne les distingue d'une phrase légitime, et l'ADR d'A31 le dit déjà
# pour le reste du dépôt : la grille sert à relire, les sept tics servent à refuser.
#
# **Un corps vide.** Qu'une PR doive porter un corps est une décision que personne n'a prise ici, et
# ce garde ne la prendra pas à sa place. Son auto-test l'épingle, pour qu'on le sache voulu.
#
# **Le corps d'une ISSUE.** Une issue n'a pas de contrôle qui puisse rougir : un atelier déclenché
# sur `issues` finirait rouge dans un onglet que personne n'ouvre. Ce corps-là reste tenu par la
# relecture et par la fonction de garde de `ouvrir-une-issue`.
#
# Usage : verifie-corps-pr.sh "<corps>"   (sortie 0 si conforme, 1 sinon)
set -euo pipefail


# ⟨locale⟩ Le verdict de ce garde ne doit pas dependre de l endroit d ou on l appelle. `grep -E` fait
# entrer `e` dans la plage `[a-z]` sous une locale francaise et l en sort sous `C` : le meme titre
# passait en local et echouait en CI (#4456). `C.UTF-8` fixe les deux : collation deterministe, et
# les octets multi-octets restent lisibles la ou le motif en a besoin.
export LC_ALL=C.UTF-8
CORPS="${1-}"

if [ "${CORPS}" = "--auto-test" ]; then
    # Les glyphes se CONSTRUISENT plutôt que de s'écrire, pour que ce fichier n'en porte aucun :
    # c'est l'idiome de `verifie-titre-pr.sh`, sans quoi le garde des cadratins compterait la prose
    # de son propre garde.
    CADRATIN=$(printf '\u2014')
    COURBE=$(printf '\u2019')
    TROISQUOTES='```'
    echecs=0
    cas=0
    rouges=0
    verifie() { # <attendu> <corps> <libellé>
        code=0
        cas=$((cas + 1))
        if [ "$1" != 0 ]; then rouges=$((rouges + 1)); fi
        "$0" "$2" >/dev/null 2>&1 || code=$?
        if [ "${code}" = "$1" ]; then
            echo "  ✔ $3"
        else
            echo "  ✘ $3 : attendu $1, obtenu ${code}"
            echecs=1
        fi
    }

    verifie 0 "Ce corps dit ce qui a ete fait, et pourquoi." "un corps conforme passe"
    verifie 1 "Le seuil tient ${CADRATIN} la mesure le dit." "un cadratin de prose est refusé"
    verifie 1 "L${COURBE}apostrophe courbe est refusée." "une apostrophe courbe est refusée"
    verifie 1 "Le garde tient, l ADR le dit." "une élision sans apostrophe est refusée"

    # Les exemptions. Un corps de PR colle des sorties de commande et cite des glyphes : les refuser
    # rendrait le garde contournable par suppression de la citation, ce qui est pire que muet.
    verifie 0 "Sortie collee :
${TROISQUOTES}
verdict ${CADRATIN} 0 regle, l ADR absente
${TROISQUOTES}" "un bloc de code cloture est épargné"
    verifie 0 "Le glyphe de valeur absente s'ecrit « ${CADRATIN} »." "le glyphe entre guillemets français est épargné"
    verifie 0 "Le motif \`[-${CADRATIN}]\` accepte les deux formes." "le glyphe en code en ligne est épargné"

    # Contrôles NÉGATIFS : la règle de l'élision reste étroite, comme dans le garde du titre.
    verifie 0 "Le point C3 et le carre A1 sont distincts." "un code de point ne déclenche pas"
    verifie 0 "Le n° 4 est traite." "un numéro ne déclenche pas"
    verifie 0 "Deux semeurs prennent l'entree legere." "une élision correcte ne déclenche pas"
    # #4483. Une lettre isolee employee comme SYMBOLE n est pas une elision. Les quatre formes
    # mesurees sur les 21 234 lignes de prose du depot, ou elles valaient 50 faux positifs sur 120.
    verifie 0 "Le decoupage se fait a 5 s reelles, mesure a 10,5 s." "un symbole d'unité après un nombre ne déclenche pas"
    verifie 0 "Un ecran qui compose N lignes paie N requetes." "une majuscule-symbole au fil d'une phrase ne déclenche pas"
    verifie 0 "    participant S as Service" "un alias de diagramme ne déclenche pas"
    verifie 0 "Le seuil est -S info, et non warning." "un drapeau de commande ne déclenche pas"
    # La contrepartie : en TETE de phrase la majuscule reste vue, sinon « L ADR » redeviendrait muet.
    verifie 1 "Le garde tient. L amendement le dit." "une élision majuscule en tête de phrase reste refusée"

    # Épingle une DÉCISION, pas un comportement : un corps vide PASSE. Qui voudra le refuser fera
    # d'abord rougir ce cas, et lira dans l'en-tête pourquoi ce garde ne tranche pas cette
    # question-là.
    verifie 0 "" "un corps vide passe, faute de décision qui l'interdise"

    echo
    if [ "${rouges}" -eq 1 ]; then verbe=DOIT; else verbe=DOIVENT; fi
    echo "${cas} cas, dont ${rouges} qui ${verbe} rougir."
    exit "${echecs}"
fi

# Le corps passe par l'ENVIRONNEMENT et non par un tube : l'entrée standard porte déjà le
# programme python, et un corps de PR est de la donnée non fiable, qu'on n'interpole pas.
CORPS_PR="${CORPS}" python3 - <<'FIN'
import os
import re
import sys

# Construits, jamais écrits : voir l'auto-test ci-dessus.
CADRATIN = chr(0x2014)
COURBE = chr(0x2019)
OUVRANT, FERMANT = chr(0x00AB), chr(0x00BB)

corps = os.environ["CORPS_PR"]

# La prose seule. Un corps de PR colle des sorties de commande, des extraits de code et des motifs
# d'expression régulière : les lire comme de la prose ferait rougir le garde sur un corps juste.
CITE = re.compile(
    "|".join([
        "`[^`]*`",                                             # code en ligne
        re.escape(OUVRANT) + r"\s*" + re.escape(CADRATIN) + r"\s*" + re.escape(FERMANT),
    ])
)
# L elision sans apostrophe, MEME regle que le garde du titre, et meme retrecissement (#4483) :
# une lettre isolee precedee d un NOMBRE est un symbole d unite (« 143 s la »), et une MAJUSCULE
# isolee au fil d une phrase est un symbole (« les N fichiers »), jamais une elision. La majuscule
# n est donc vue qu apres une espace elle-meme precedee d un caractere non alphanumerique, soit la
# tete de phrase. Le detail de la mesure est dans `verifie-titre-pr.sh`, qui porte la regle mere.
BORNE = r"[^\w'" + COURBE + r"]"
ELISION_MIN = re.compile(r"(^" + BORNE + r"?|[^\d]" + BORNE + r")([ldnscjmt]|qu) +[^\W\d_]")
ELISION_MAJ = re.compile(r"(^" + BORNE + r"?|[^\w]\s)([LDNSCJMT]|Qu) +[^\W\d_]")

fautes = []
dans_un_bloc = False
for numero, ligne in enumerate(corps.splitlines(), 1):
    if ligne.lstrip().startswith("```"):
        dans_un_bloc = not dans_un_bloc
        continue
    if dans_un_bloc:
        continue
    prose = CITE.sub("", ligne)
    if CADRATIN in prose:
        fautes.append((numero, "tiret cadratin", ligne.strip()))
    if COURBE in prose:
        fautes.append((numero, "apostrophe courbe", ligne.strip()))
    if ELISION_MIN.search(prose) or ELISION_MAJ.search(prose):
        fautes.append((numero, "élision sans apostrophe", ligne.strip()))

if not fautes:
    print("Corps conforme.")
    sys.exit(0)

REMEDES = {
    "tiret cadratin": (
        "Écrivez un deux-points, une virgule ou un tiret simple. La zone de prose du dépôt est à\n"
        "  tolérance zéro, et ce corps est publié sur la forge : il ne se retire pas.\n"
        "  Cf. dev-docs/decisions/typographie-cliquet-plutot-que-nettoyage.md"
    ),
    "apostrophe courbe": (
        "Le dépôt n'écrit que l'apostrophe droite ('), et ce corps ne fait pas exception.\n"
        "  Cf. dev-docs/decisions/le-depot-n-ecrit-qu-une-apostrophe.md"
    ),
    "élision sans apostrophe": (
        "Rétablissez l'apostrophe : « l'ADR », « d'une nuit », « n'est pas », « qu'il ».\n"
        "  L'habitude d'amputer les apostrophes vient du quoting shell des messages de commit ;\n"
        "  elle survit à la disparition de sa cause, et ce corps-ci se lit sur la forge."
    ),
}

print(f"::error::Le corps de la PR porte {len(fautes)} defaut(s) de typographie.")
print()
for numero, defaut, ligne in fautes:
    print(f"  ligne {numero} : {defaut}")
    print(f"    {ligne[:110]}")
print()
for defaut in dict.fromkeys(d for _, d, _ in fautes):
    print(f"  {defaut} : {REMEDES[defaut]}")
    print()
print("Ce corps est de la prose visible au sens de l'article A31 : il se relit dans six mois sans le")
print("fil, et il est publié dès qu'il part. La grille complète est dans la compétence humaniseur ;")
print("ce garde ne tient que ce qu'un motif peut voir.")
sys.exit(1)
FIN
