# 📹 Instructions pour Intégrer les Vidéos dans le README

## Problème
GitHub ne supporte pas les balises HTML `<video>` dans les fichiers Markdown pour des raisons de sécurité.
Les vidéos stockées avec Git LFS ne s'affichent pas directement dans le README.

## ✅ Solution : Utiliser GitHub User Attachments

### Étape 1 : Créer une Issue Temporaire

1. Allez sur votre repository GitHub :
   ```
   https://github.com/tourem/deploy-manifest-plugin/issues
   ```

2. Cliquez sur **"New Issue"**

3. Titre : `[TEMP] Video Upload for README`

### Étape 2 : Uploader les Vidéos

1. Dans le corps de l'issue, **glissez-déposez** les 2 vidéos :
   - `videos/Maven_Deploy_Manifest_Plugin_fr.mp4`
   - `videos/Maven_Deploy_Manifest_Plugin_eng.mp4`

2. GitHub va uploader les vidéos et générer automatiquement des liens comme :
   ```
   https://github.com/user-attachments/assets/XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX
   ```

3. **IMPORTANT** : Ne fermez pas l'issue tout de suite, copiez d'abord les liens !

### Étape 3 : Copier les Liens Générés

Après l'upload, vous verrez dans le corps de l'issue quelque chose comme :

```markdown
https://github.com/user-attachments/assets/12345678-1234-1234-1234-123456789abc

https://github.com/user-attachments/assets/87654321-4321-4321-4321-cba987654321
```

**Copiez ces 2 URLs** (une pour chaque vidéo).

### Étape 4 : Mettre à Jour le README

Remplacez dans le README.md la section vidéo par :

```markdown
### 🎥 Video Demonstrations

Watch the plugin in action with complete walkthroughs:

**🇫🇷 Version Française:**

https://github.com/user-attachments/assets/VOTRE-URL-VIDEO-FR

**🇬🇧 English Version:**

https://github.com/user-attachments/assets/VOTRE-URL-VIDEO-EN
```

### Étape 5 : Fermer l'Issue Temporaire

Une fois les URLs copiées et le README mis à jour, vous pouvez fermer l'issue temporaire.

---

## 🎬 Résultat Attendu

Avec cette méthode, les vidéos s'afficheront **directement dans le README** avec :
- ✅ Lecteur vidéo natif intégré
- ✅ Contrôles play/pause/volume
- ✅ Barre de progression
- ✅ Pas besoin de télécharger

---

## 🔄 Alternative : Héberger sur YouTube/Vimeo

Si vous préférez, vous pouvez aussi :

1. Uploader les vidéos sur **YouTube** ou **Vimeo**
2. Utiliser une image de prévisualisation avec lien :

```markdown
**🇫🇷 Version Française:**

[![French Demo](https://img.youtube.com/vi/VIDEO_ID/maxresdefault.jpg)](https://www.youtube.com/watch?v=VIDEO_ID)
```

---

## 📝 Commandes pour Vous

Après avoir obtenu les URLs GitHub :

```bash
cd /Users/mtoure/dev/maven-flow
git checkout docs/add-demo-videos

# Éditez README.md avec les nouvelles URLs
# Puis :

git add README.md
git commit -m "docs: Update video URLs with GitHub user-attachments links"
git push origin docs/add-demo-videos
```

---

## ❓ Besoin d'Aide ?

Dites-moi quand vous avez les URLs des vidéos uploadées via l'issue GitHub, 
et je mettrai à jour le README automatiquement !

