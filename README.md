# Larsons-Game

This is a descriptive plan for a new game that uses mechanics from Larsons Game Engine. Each section is broken up into chunks or task to be done.
Task 1: Create the game layout
  -	This game will be a 3D environment with 2D entities for the player and items.
  -	The player will render from 8 directions like the sprites from Larsons Game Engine. The player will also have 3 vertical rendering zones split into thirds for the different 3rd person heights you can view the character from (side at 0 degrees from 0 to 33 degrees         above the character, middle at 45 degrees from 34 degrees to 75 degrees, and top down or birds-eye at 90 degrees above the character from 75 degrees to 90 degrees). Zoom in/out should also be a feature.
  -	All characters (playable or otherwise) will be rendered as a series of “stackable” sprite sheets. The elements for each character will line up with their animations. For example, a sprite-sheet for a held sword will render in-hand for a player character. The player         character and sword sprite-sheets will be the same number of frames (30fps, 512x512 pixel frames) for any given animation state, and the sword will follow the characters movements when held. The sword will be cut out where the player’s hand is located, allowing the        sword to be rendered appropriately in the stack of sprite-sheets. The camera should rotate and snap to these 8 directional points. These sprite sheets have all been pre-rendered in Blender. For any further clarifications please ask. 
  -	Items will be 2D sprites as well that hover with a shadow on the ground. They will always face the camera from any angle.
  -	All other environmental elements (trees, rocks, houses, etc) will be rendered in 3D. 
  -	TO DO:
  o	Create a blank 3D void area that can render the animations (all 8 points and 3 heights for each) for the character sprite in the following states: idle, walk, run, sprint, jump, attack
  o	Create room for cosmetics as sprite-sheet layers over the base character sprite-sheet. This includes rendering sprite-sheets for: shirts, pants, underwear, bras, shoes, hairs, noses, eyes, mouths, ears, earrings, wristwear, gloves, hats, carrying items (left and right     hand), and an “other” category. These should all be editable from a generic pause menu. Again, all cosmetics are just overlain sprite sheets rendered in Blender.
  o	Render one “pick-up-able” item in the scene (a sword that renders in hand when equipped)
  o	While the sprite-sheets will be 512x512 pixels per frame (and vary in frame length based on the animation state), please create a fallback profile for each animation if the base player character is not provided. These fallback animations only need to be roughly 32x32     pixels but scaled up. Cosmetics will be left blank if not provided.
  o	Make a main menu scene that only loads “demo” for the blank void scene.
  o	Create a drag and drop file system for saving sprite-sheets to the repository
  o	Copy over all pertinent code and enable GPU acceleration for the entire game
  o	Create an IntelliJ profile to run the game with GPU acceleration on
  o	Refine the README.md with added features and polish the formatting
Task 2: TBD
