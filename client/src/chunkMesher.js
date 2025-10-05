import * as THREE from 'three'

export function buildChunkGeometry({ data, chunkSize, yMin, yMax }) {
  const height = yMax - yMin
  const getIndex = (x, yLocal, z) => ((yLocal * chunkSize + z) * chunkSize + x)
  const isSolidLocal = (x, yLocal, z) => {
    if (x < 0 || x >= chunkSize || z < 0 || z >= chunkSize || yLocal < 0 || yLocal >= height) return false
    return data[getIndex(x, yLocal, z)] !== 0
  }

  const positions = []
  const normals = []
  const colors = []
  const indices = []

  const addFace = (ax, ay, az, bx, by, bz, cx, cy, cz, dx, dy, dz, nx, ny, nz, shade) => {
    const base = positions.length / 3
    positions.push(ax, ay, az, bx, by, bz, cx, cy, cz, dx, dy, dz)
    normals.push(nx, ny, nz, nx, ny, nz, nx, ny, nz, nx, ny, nz)
    const c = new THREE.Color().setHSL(0.35, 0.5, shade)
    for (let i = 0; i < 4; i++) colors.push(c.r, c.g, c.b)
    indices.push(base, base + 1, base + 2, base, base + 2, base + 3)
  }

  for (let yLocal = 0; yLocal < height; yLocal++) {
    for (let z = 0; z < chunkSize; z++) {
      for (let x = 0; x < chunkSize; x++) {
        if (!isSolidLocal(x, yLocal, z)) continue
        const wy = yLocal + yMin
        // neighbors
        const nxSolid = isSolidLocal(x - 1, yLocal, z)
        const pxSolid = isSolidLocal(x + 1, yLocal, z)
        const nySolid = isSolidLocal(x, yLocal - 1, z)
        const pySolid = isSolidLocal(x, yLocal + 1, z)
        const nzSolid = isSolidLocal(x, yLocal, z - 1)
        const pzSolid = isSolidLocal(x, yLocal, z + 1)

        // -X face
        if (!nxSolid) addFace(
          x, wy, z + 1,
          x, wy + 1, z + 1,
          x, wy + 1, z,
          x, wy, z,
          -1, 0, 0, 0.45
        )
        // +X face
        if (!pxSolid) addFace(
          x + 1, wy, z,
          x + 1, wy + 1, z,
          x + 1, wy + 1, z + 1,
          x + 1, wy, z + 1,
          1, 0, 0, 0.5
        )
        // -Y face
        if (!nySolid) addFace(
          x, wy, z + 1,
          x + 1, wy, z + 1,
          x + 1, wy, z,
          x, wy, z,
          0, -1, 0, 0.35
        )
        // +Y face (top)
        if (!pySolid) addFace(
          x, wy + 1, z,
          x + 1, wy + 1, z,
          x + 1, wy + 1, z + 1,
          x, wy + 1, z + 1,
          0, 1, 0, 0.7
        )
        // -Z face
        if (!nzSolid) addFace(
          x, wy + 1, z,
          x + 1, wy + 1, z,
          x + 1, wy, z,
          x, wy, z,
          0, 0, -1, 0.4
        )
        // +Z face
        if (!pzSolid) addFace(
          x, wy, z + 1,
          x + 1, wy, z + 1,
          x + 1, wy + 1, z + 1,
          x, wy + 1, z + 1,
          0, 0, 1, 0.55
        )
      }
    }
  }

  const geometry = new THREE.BufferGeometry()
  geometry.setAttribute('position', new THREE.Float32BufferAttribute(positions, 3))
  geometry.setAttribute('normal', new THREE.Float32BufferAttribute(normals, 3))
  geometry.setAttribute('color', new THREE.Float32BufferAttribute(colors, 3))
  geometry.setIndex(indices)
  geometry.computeBoundingSphere()
  return geometry
}
