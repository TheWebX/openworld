import * as THREE from 'three'

const app = document.getElementById('app')
const overlay = document.getElementById('overlay')

// Three.js setup
const renderer = new THREE.WebGLRenderer({ antialias: true })
renderer.setPixelRatio(Math.min(devicePixelRatio, 2))
renderer.setSize(window.innerWidth, window.innerHeight)
app.appendChild(renderer.domElement)

const scene = new THREE.Scene()
scene.background = new THREE.Color('#87ceeb')

const camera = new THREE.PerspectiveCamera(70, window.innerWidth / window.innerHeight, 0.1, 1000)
scene.add(camera)

const ambient = new THREE.AmbientLight(0xffffff, 0.6)
scene.add(ambient)
const sun = new THREE.DirectionalLight(0xffffff, 0.7)
sun.position.set(0.5, 1, 0.3)
scene.add(sun)

// Ground grid
const grid = new THREE.GridHelper(400, 200, 0x444444, 0x888888)
scene.add(grid)

// Player visuals
const playerMeshes = new Map()
let myId = null
function getOrCreatePlayerMesh(id) {
  let mesh = playerMeshes.get(id)
  if (!mesh) {
    const geom = new THREE.BoxGeometry(0.6, 1.8, 0.6)
    const mat = new THREE.MeshLambertMaterial({ color: id === myId ? 0x33aaff : 0xffcc66 })
    mesh = new THREE.Mesh(geom, mat)
    mesh.castShadow = false
    mesh.receiveShadow = false
    scene.add(mesh)
    playerMeshes.set(id, mesh)
  }
  return mesh
}

// Input handling (pointer lock + WASD)
let pointerLocked = false
let yaw = 0, pitch = 0
const pressed = new Set()
let wantJump = false
let sprint = false

window.addEventListener('resize', () => {
  camera.aspect = window.innerWidth / window.innerHeight
  camera.updateProjectionMatrix()
  renderer.setSize(window.innerWidth, window.innerHeight)
})

document.addEventListener('keydown', (e) => {
  pressed.add(e.code)
  if (e.code === 'Space') wantJump = true
  if (e.code === 'ShiftLeft' || e.code === 'ShiftRight') sprint = true
})

document.addEventListener('keyup', (e) => {
  pressed.delete(e.code)
  if (e.code === 'ShiftLeft' || e.code === 'ShiftRight') sprint = false
})

document.addEventListener('click', () => {
  if (!pointerLocked) renderer.domElement.requestPointerLock()
})

document.addEventListener('pointerlockchange', () => {
  pointerLocked = document.pointerLockElement === renderer.domElement
  overlay.classList.toggle('hidden', pointerLocked)
})

document.addEventListener('mousemove', (e) => {
  if (!pointerLocked) return
  const sensitivity = 0.002
  yaw -= e.movementX * sensitivity
  pitch -= e.movementY * sensitivity
  pitch = Math.max(-Math.PI / 2 + 0.01, Math.min(Math.PI / 2 - 0.01, pitch))
})

function computeInputAxes() {
  let forwardX = Math.sin(yaw)
  let forwardZ = Math.cos(yaw)
  let leftX = forwardZ
  let leftZ = -forwardX
  let moveX = 0, moveZ = 0
  if (pressed.has('KeyW')) { moveX += forwardX; moveZ += forwardZ }
  if (pressed.has('KeyS')) { moveX -= forwardX; moveZ -= forwardZ }
  if (pressed.has('KeyA')) { moveX += leftX; moveZ += leftZ }
  if (pressed.has('KeyD')) { moveX -= leftX; moveZ -= leftZ }
  const len = Math.hypot(moveX, moveZ)
  if (len > 0) { moveX /= len; moveZ /= len }
  return { ax: moveX, az: moveZ }
}

// WebSocket setup
let socket = null
function connect() {
  socket = new WebSocket(`ws://${location.hostname}:8080/ws`)
  socket.addEventListener('open', () => {
    console.log('connected')
  })
  socket.addEventListener('message', (ev) => {
    const msg = JSON.parse(ev.data)
    if (msg.type === 'hello') {
      myId = msg.id
    } else if (msg.type === 'state') {
      handleState(msg)
    }
  })
  socket.addEventListener('close', () => {
    setTimeout(connect, 1000)
  })
}
connect()

function handleState(msg) {
  if (!myId) return
  const players = msg.players
  for (const p of players) {
    const mesh = getOrCreatePlayerMesh(p.id)
    mesh.position.set(p.x, p.y, p.z)
    if (p.id === myId) {
      const eyeHeight = 1.6
      camera.position.set(p.x, p.y + eyeHeight, p.z)
      camera.rotation.set(pitch, yaw, 0, 'YXZ')
    }
  }
}

// Send inputs at 30 Hz
setInterval(() => {
  if (!socket || socket.readyState !== WebSocket.OPEN) return
  const { ax, az } = computeInputAxes()
  const jump = wantJump; wantJump = false
  socket.send(JSON.stringify({ type: 'input', input: { ax, az, jump, sprint } }))
}, 33)

// Render loop
function animate() {
  requestAnimationFrame(animate)
  renderer.render(scene, camera)
}
animate()
