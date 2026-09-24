import numpy as np
import trimesh
from trimesh.transformations import rotation_matrix, translation_matrix
from trimesh.visual import TextureVisuals
from trimesh.visual.material import PBRMaterial

scene = trimesh.Scene()


def pbr(name, rgba, metallic=0.15, roughness=0.52, emissive=None):
    kwargs = {}
    if emissive is not None:
        kwargs['emissiveFactor'] = [c / 255.0 for c in emissive[:3]]
    return PBRMaterial(
        name=name,
        baseColorFactor=np.array(rgba, dtype=np.uint8),
        metallicFactor=metallic,
        roughnessFactor=roughness,
        doubleSided=True,
        **kwargs,
    )


def add(name, mesh, material, transform=None):
    mesh = mesh.copy()
    mesh.visual = TextureVisuals(material=material)
    if transform is not None:
        mesh.apply_transform(transform)
    scene.add_geometry(mesh, node_name=name, geom_name=name)

body_mat = pbr('DroneBody', [54, 62, 74, 255], metallic=0.28, roughness=0.42)
shell_mat = pbr('DroneShell', [93, 104, 120, 255], metallic=0.22, roughness=0.38)
dark_mat = pbr('DroneDark', [24, 29, 36, 255], metallic=0.18, roughness=0.50)
rotor_mat = pbr('DroneRotor', [104, 116, 132, 255], metallic=0.32, roughness=0.34)
accent_mat = pbr('DroneAccent', [238, 172, 65, 255], metallic=0.05, roughness=0.44, emissive=[102, 55, 10, 255])

# Cuerpo central y cubierta.
body = trimesh.creation.icosphere(subdivisions=2, radius=1.0)
body.apply_scale([0.18, 0.095, 0.24])
add('body', body, body_mat)

top = trimesh.creation.icosphere(subdivisions=2, radius=1.0)
top.apply_scale([0.125, 0.052, 0.155])
add('top_shell', top, shell_mat, translation_matrix([0, 0.073, -0.006]))

camera = trimesh.creation.icosphere(subdivisions=2, radius=0.052)
add('camera', camera, dark_mat, translation_matrix([0, -0.016, 0.226]))

indicator = trimesh.creation.icosphere(subdivisions=2, radius=0.028)
add('indicator', indicator, accent_mat, translation_matrix([0, 0.022, 0.266]))

# Brazos cruzados.
for i, angle in enumerate((45, -45)):
    arm = trimesh.creation.box(extents=[0.72, 0.038, 0.050])
    add(f'arm_{i}', arm, body_mat, rotation_matrix(np.deg2rad(angle), [0, 1, 0]))

rotor_positions = [
    (0.252, 0.030, 0.252),
    (-0.252, 0.030, 0.252),
    (0.252, 0.030, -0.252),
    (-0.252, 0.030, -0.252),
]
for i, (x, y, z) in enumerate(rotor_positions):
    hub = trimesh.creation.cylinder(radius=0.043, height=0.064, sections=24)
    hub_transform = rotation_matrix(np.deg2rad(90), [1, 0, 0])
    hub_transform[:3, 3] = [x, y, z]
    add(f'hub_{i}', hub, dark_mat, hub_transform)

    rotor = trimesh.creation.cylinder(radius=0.132, height=0.010, sections=32)
    rotor_transform = rotation_matrix(np.deg2rad(90), [1, 0, 0])
    rotor_transform[:3, 3] = [x, y + 0.050, z]
    add(f'rotor_{i}', rotor, rotor_mat, rotor_transform)

    foot = trimesh.creation.box(extents=[0.030, 0.135, 0.030])
    add(
        f'leg_{i}',
        foot,
        dark_mat,
        translation_matrix([x * 0.63, -0.118, z * 0.63]),
    )

for side in (-1, 1):
    skid = trimesh.creation.box(extents=[0.040, 0.030, 0.38])
    add(
        f'skid_{side}',
        skid,
        dark_mat,
        translation_matrix([0.135 * side, -0.195, 0]),
    )

out = 'app/src/main/assets/models/guide_drone.glb'
with open(out, 'wb') as f:
    f.write(trimesh.exchange.gltf.export_glb(scene, include_normals=True))

loaded = trimesh.load(out, force='scene')
print(out)
print('geometry:', len(loaded.geometry))
print('bounds:', loaded.bounds.tolist())
print('size bytes:', __import__('os').path.getsize(out))
