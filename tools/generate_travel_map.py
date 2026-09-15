from pathlib import Path

import numpy as np
import trimesh
from scipy.spatial import Delaunay
from shapely.geometry import Point, Polygon

# Silueta simplificada del estado para mantener el asset ligero en Quest.
BOUNDARY_LON_LAT = [
    (-106.51, 31.77), (-105.93, 31.33), (-105.42, 30.90), (-104.85, 30.44),
    (-104.44, 29.59), (-104.06, 29.34), (-103.48, 29.08), (-103.31, 29.03),
    (-103.64, 28.47), (-103.96, 27.92), (-103.74, 27.06), (-103.63, 26.64),
    (-104.23, 26.72), (-104.52, 26.37), (-104.99, 26.50), (-105.31, 26.46),
    (-105.81, 26.69), (-106.03, 26.84), (-106.16, 26.74), (-106.43, 26.36),
    (-106.38, 26.13), (-106.53, 25.81), (-106.72, 25.63), (-107.08, 25.73),
    (-107.34, 26.08), (-107.76, 26.19), (-107.84, 26.60), (-108.01, 26.90),
    (-108.37, 27.08), (-108.68, 27.18), (-108.67, 27.54), (-108.91, 27.84),
    (-109.15, 28.18), (-109.07, 28.28), (-108.54, 28.32), (-108.65, 28.61),
    (-108.62, 28.77), (-108.71, 29.40), (-108.56, 30.00), (-108.58, 30.51),
    (-109.03, 30.80), (-109.00, 31.08), (-108.97, 31.33), (-108.21, 31.33),
    (-108.21, 31.78), (-107.59, 31.78), (-107.15, 31.78), (-106.51, 31.77),
]

MIN_LON, MAX_LON = -109.15, -103.31
MIN_LAT, MAX_LAT = 25.63, 31.78
MAP_WIDTH = 2.35
MAP_DEPTH = 2.70
BASE_HEIGHT = 0.055


def project(lon: float, lat: float) -> tuple[float, float]:
    x = -(((lon - MIN_LON) / (MAX_LON - MIN_LON) - 0.5) * MAP_WIDTH)
    z = ((lat - MIN_LAT) / (MAX_LAT - MIN_LAT) - 0.5) * MAP_DEPTH
    return x, z


def terrain_height(x: np.ndarray, z: np.ndarray) -> np.ndarray:
    base = np.full_like(x, BASE_HEIGHT, dtype=float)
    ridges = [
        (0.72, -0.36, 0.17, 0.34, 0.24),
        (0.66, 0.02, 0.20, 0.30, 0.28),
        (0.58, 0.42, 0.16, 0.28, 0.26),
        (0.37, -0.67, 0.10, 0.28, 0.24),
        (0.30, 0.68, 0.08, 0.38, 0.30),
        (0.02, -0.18, 0.06, 0.44, 0.34),
    ]
    valleys = [
        (-0.18, 0.22, 0.035, 0.26, 0.24),
        (-0.04, -0.46, 0.028, 0.22, 0.22),
    ]
    for cx, cz, amplitude, sx, sz in ridges:
        base += amplitude * np.exp(-(((x - cx) / sx) ** 2 + ((z - cz) / sz) ** 2))
    for cx, cz, amplitude, sx, sz in valleys:
        base -= amplitude * np.exp(-(((x - cx) / sx) ** 2 + ((z - cz) / sz) ** 2))
    base += 0.018 * np.sin(x * 7.0 + z * 2.2) * np.cos(z * 6.0)
    base += 0.010 * np.sin((x + z) * 10.5) + 0.006 * np.cos((x * 1.8 - z * 0.9) * 8.0)
    return np.clip(base, BASE_HEIGHT - 0.010, 0.245)


def build_top_mesh(boundary_xz: np.ndarray) -> trimesh.Trimesh:
    polygon = Polygon(boundary_xz)
    minx, minz, maxx, maxz = polygon.bounds
    xs = np.linspace(minx, maxx, 43)
    zs = np.linspace(minz, maxz, 49)
    interior = [
        (x, z)
        for z in zs
        for x in xs
        if polygon.buffer(-0.008).contains(Point(float(x), float(z)))
    ]
    points = np.vstack([boundary_xz[:-1], np.asarray(interior, dtype=float)])
    delaunay = Delaunay(points)
    faces = []
    for triangle in delaunay.simplices:
        centroid = points[triangle].mean(axis=0)
        if polygon.covers(Point(float(centroid[0]), float(centroid[1]))):
            faces.append(triangle)

    heights = terrain_height(points[:, 0], points[:, 1])
    vertices = np.column_stack([points[:, 0], heights, points[:, 1]])
    top_faces = np.asarray(faces, dtype=np.int64)[:, ::-1]
    mesh = trimesh.Trimesh(vertices=vertices, faces=top_faces, process=True)
    mesh.visual.material = trimesh.visual.material.PBRMaterial(
        name="ChihuahuaTerrain",
        baseColorFactor=[160, 115, 67, 255],
        metallicFactor=0.06,
        roughnessFactor=0.58,
    )
    return mesh


def build_side_mesh(boundary_xz: np.ndarray) -> trimesh.Trimesh:
    ring = boundary_xz[:-1]
    top_y = terrain_height(ring[:, 0], ring[:, 1])
    vertices = []
    faces = []
    count = len(ring)
    for i in range(count):
        j = (i + 1) % count
        base = len(vertices)
        vertices.extend([
            (ring[i, 0], 0.0, ring[i, 1]),
            (ring[j, 0], 0.0, ring[j, 1]),
            (ring[j, 0], top_y[j], ring[j, 1]),
            (ring[i, 0], top_y[i], ring[i, 1]),
        ])
        faces.extend([(base, base + 1, base + 2), (base, base + 2, base + 3)])
    mesh = trimesh.Trimesh(vertices=np.asarray(vertices), faces=np.asarray(faces), process=True)
    mesh.visual.material = trimesh.visual.material.PBRMaterial(
        name="ChihuahuaEdge",
        baseColorFactor=[66, 44, 27, 255],
        metallicFactor=0.20,
        roughnessFactor=0.52,
    )
    return mesh


def build_flat_mesh(boundary_xz: np.ndarray, top_height: float, bottom_height: float, buffer_amount: float,
                    top_rgba: list[int], side_rgba: list[int], top_name: str, side_name: str) -> tuple[trimesh.Trimesh, trimesh.Trimesh]:
    polygon = Polygon(boundary_xz).buffer(buffer_amount, join_style=2)
    boundary = np.asarray(polygon.exterior.coords)
    minx, minz, maxx, maxz = polygon.bounds
    xs = np.linspace(minx, maxx, 25)
    zs = np.linspace(minz, maxz, 29)
    interior = [
        (x, z)
        for z in zs
        for x in xs
        if polygon.buffer(-0.008).contains(Point(float(x), float(z)))
    ]
    points = np.vstack([boundary[:-1], np.asarray(interior, dtype=float)]) if interior else boundary[:-1]
    delaunay = Delaunay(points)
    faces = []
    for triangle in delaunay.simplices:
        centroid = points[triangle].mean(axis=0)
        if polygon.covers(Point(float(centroid[0]), float(centroid[1]))):
            faces.append(triangle)
    top_vertices = np.column_stack([points[:, 0], np.full(len(points), top_height), points[:, 1]])
    top_mesh = trimesh.Trimesh(vertices=top_vertices, faces=np.asarray(faces, dtype=np.int64)[:, ::-1], process=True)
    top_mesh.visual.material = trimesh.visual.material.PBRMaterial(
        name=top_name,
        baseColorFactor=top_rgba,
        metallicFactor=0.18,
        roughnessFactor=0.40,
    )

    ring = boundary[:-1]
    vertices = []
    side_faces = []
    count = len(ring)
    for i in range(count):
        j = (i + 1) % count
        base = len(vertices)
        vertices.extend([
            (ring[i, 0], bottom_height, ring[i, 1]),
            (ring[j, 0], bottom_height, ring[j, 1]),
            (ring[j, 0], top_height, ring[j, 1]),
            (ring[i, 0], top_height, ring[i, 1]),
        ])
        side_faces.extend([(base, base + 1, base + 2), (base, base + 2, base + 3)])
    side_mesh = trimesh.Trimesh(vertices=np.asarray(vertices), faces=np.asarray(side_faces), process=True)
    side_mesh.visual.material = trimesh.visual.material.PBRMaterial(
        name=side_name,
        baseColorFactor=side_rgba,
        metallicFactor=0.24,
        roughnessFactor=0.34,
    )
    return top_mesh, side_mesh


def main() -> None:
    boundary_xz = np.asarray([project(lon, lat) for lon, lat in BOUNDARY_LON_LAT])
    top = build_top_mesh(boundary_xz)
    sides = build_side_mesh(boundary_xz)
    pedestal_top, pedestal_side = build_flat_mesh(
        boundary_xz,
        top_height=0.026,
        bottom_height=-0.052,
        buffer_amount=0.085,
        top_rgba=[86, 58, 36, 255],
        side_rgba=[28, 18, 11, 255],
        top_name="ChihuahuaPedestalTop",
        side_name="ChihuahuaPedestalSide",
    )
    scene = trimesh.Scene()
    scene.add_geometry(pedestal_top, node_name="pedestal_top")
    scene.add_geometry(pedestal_side, node_name="pedestal_side")
    scene.add_geometry(top, node_name="terrain")
    scene.add_geometry(sides, node_name="edge")

    output = Path(__file__).resolve().parents[1] / "app/src/main/assets/models/chihuahua_travel_map.glb"
    output.parent.mkdir(parents=True, exist_ok=True)
    def prepare_materials(tree: dict) -> None:
        for index, material in enumerate(tree.get("materials", [])):
            material["doubleSided"] = True
            material["emissiveFactor"] = (
                [0.028, 0.015, 0.006] if index < 2 else [0.045, 0.022, 0.010]
            )

    output.write_bytes(
        trimesh.exchange.gltf.export_glb(
            scene,
            include_normals=True,
            tree_postprocessor=prepare_materials,
        )
    )
    print(f"Generated {output} ({output.stat().st_size / 1024:.1f} KiB)")
    print("Bounds:", scene.bounds)


if __name__ == "__main__":
    main()
