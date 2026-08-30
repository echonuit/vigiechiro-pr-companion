---
name: recoller-la-doc-au-code
description: Use at closure pass 3, once the CLI and screen surfaces agree, to make the developer documentation match the code that was delivered. The first move is finding what became FALSE, not what is missing, and the instrument starts from the touched files rather than from memory.
license: GPL-3.0-or-later
metadata:
  langue: fr
  origine: dev-docs/cycle-de-chantier.md
---

# Recoller la doc au code

## Loi d'airain

```
ON CHERCHE CE QUI EST DEVENU FAUX, PAS CE QU'ON A À AJOUTER
```

« Mettre à jour la doc » se lit spontanément comme « qu'ai-je à **ajouter** ? ». Le mode de panne est
l'inverse : une page qui décrivait fidèlement un mécanisme **remplacé** ne signale rien, ne rougit
nulle part, et se lit comme vraie.

## Annoncer

« J'utilise la compétence recoller-la-doc-au-code sur ce que <le chantier> a livré. »

## Fonction de garde

```
1. PARTIR    des fichiers que le chantier a TOUCHES, jamais de sa memoire.
2. CHERCHER  qui les cite, dans docs, dev-docs et brief.
3. LIRE      la sortie par motif, du plus RARE au plus frequent.
4. OUVRIR    les pages que les motifs a faible rendement designent.
5. CORRIGER  ce qui est devenu faux, ou le SUPPRIMER. Une page a moitie vraie
             egare plus qu une page absente.
6. AJOUTER   ce qui manque, une fois seulement que le faux est traite.
```

Les ADR ne s'écrivent pas ici : elles se rédigent en passe 11, quand toutes les décisions sont
prises. Cette passe ne traite que les pages de description.

## L'instrument

```bash
git diff --name-only <sha-d-ouverture>..origin/main | while read -r fichier; do
  nom=$(basename "$fichier")
  # Un fichier SOURCE se cite par son identifiant, sans extension : les pages écrivent
  # « FilAriane », jamais « FilAriane.java ». Tout autre fichier se cite par son nom entier.
  case "$nom" in
    [A-Z]*.java | [A-Z]*.fxml) motif="${nom%.*}" ;;
    *) motif="$nom" ;;
  esac
  grep -rl -- "$motif" docs dev-docs brief 2>/dev/null | while read -r page; do
    printf '%s\t%s\n' "$motif" "$page"
  done
done | sort -u
```

**La sortie se lit par motif, du plus rare au plus fréquent.** Un motif qui rend plus d'une douzaine
de pages est trop générique pour dire quoi que ce soit ; ce sont les motifs à **faible rendement** qui
désignent les pages à ouvrir.

Mesuré sur le delta du chantier #4873 : 190 paires, dont `index.md` en fournit **95 à lui seul**,
c'est-à-dire la moitié de la sortie sans désigner quoi que ce soit. Les motifs utiles y rendaient une
page chacun.

## Les deux pièges de l'instrument, tous deux mesurés

**Chercher `X.java` là où les pages citent `X`** (#3648). Sur le chantier #3798, `FilAriane.java`
rendait **zéro** page ; `FilAriane` en rend **deux**, dont `dev-docs/navigation.md`, précisément la
page que ce chantier avait rendue fausse. L'instrument manquait exactement ce que la passe existe
pour trouver.

**Et le remède évident est pire que le mal.** Retirer l'extension partout fait chercher « base » pour
`base.css` : **122** pages au lieu de 7, et « navigation » pour `navigation.md` : 39 au lieu de 5. Le
correctif ne vaut que pour les fichiers dont le nom **est un identifiant**, d'où le `case`. Un
instrument qui noie sa sortie ne ment pas moins qu'un qui rend zéro.

## Ce que le nom ne trouve pas

Un nom de classe, de script ou de fichier suffit à trouver ses mentions. Un **concept** qui n'a pas
de nom stable se cherche autrement, et la même question se pose au graphe du dépôt, qui relie code,
doc et brief.

```bash
graphify query "quelles pages décrivent <le concept> ?" --budget 2500
```

## Le cas qui a établi la loi d'airain

Le chantier #3439 a remplacé les rectangles de masque des aperçus, écrits à la main, par des
rectangles dérivés de la scène.

`dev-docs/captures.md` a continué **pendant une semaine** à décrire « seize » fichiers énumérés dans
un script, et à qualifier de « non élucidée » une instabilité que le même chantier venait d'élucider.

Rien ne l'a signalé. C'est un lecteur qui l'a trouvé.

## Signaux d'alerte : on s'arrête

| Pensée | Réalité |
|---|---|
| « Qu'ai-je à ajouter à la doc ? » | La question est ce qui est devenu **faux**. L'ajout vient après |
| « Je relis les pages que je connais » | On part des fichiers touchés, pas de la mémoire |
| « Le motif rend cinquante pages » | Il est trop générique. Ce sont les motifs rares qui désignent |
| « `X.java` ne rend aucune page » | Les pages citent `X`. Zéro est le symptôme du mauvais motif |
| « Cette page n'est fausse qu'à moitié » | Elle égare plus qu'une page absente. On corrige ou on supprime |
| « C'est un concept, `grep` ne trouve rien » | La question se pose au graphe, qui relie doc, code et brief |
| « J'écris l'ADR pendant que j'y suis » | Elles se rédigent en passe 11, quand toutes les décisions sont prises |
