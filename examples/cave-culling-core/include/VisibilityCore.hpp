#pragma once

// Fast Client - engine-independent cave-section visibility core.
// This is NOT a Minecraft renderer hook. The game must supply opaque-block
// snapshots and use the result to skip draw submissions at its render boundary.
#include <array>
#include <cstddef>
#include <cstdint>
#include <queue>
#include <unordered_map>
#include <unordered_set>
#include <vector>

namespace fastclient::culling {

constexpr int SIDE = 16;
constexpr int VOLUME = SIDE * SIDE * SIDE;
using OpaqueBlocks = std::array<std::uint8_t, VOLUME>;

enum class Face : std::uint8_t { NegX, PosX, NegY, PosY, NegZ, PosZ };
constexpr int faceIndex(Face face) { return static_cast<int>(face); }
constexpr std::uint8_t faceBit(Face face) {
    return static_cast<std::uint8_t>(1u << faceIndex(face));
}
constexpr Face opposite(Face face) {
    return static_cast<Face>(faceIndex(face) ^ 1);
}
constexpr int index(int x, int y, int z) { return (y * SIDE + z) * SIDE + x; }

struct Coord {
    int x = 0;
    int y = 0;
    int z = 0;
    bool operator==(const Coord &other) const {
        return x == other.x && y == other.y && z == other.z;
    }
};

struct CoordHash {
    std::size_t operator()(const Coord &p) const noexcept {
        auto mix = [](std::size_t n) { return n ^ (n >> 16); };
        std::size_t h = mix(static_cast<std::uint32_t>(p.x)) * 0x9E3779B185EBCA87ULL;
        h ^= mix(static_cast<std::uint32_t>(p.y)) * 0xC2B2AE3D27D4EB4FULL;
        h ^= mix(static_cast<std::uint32_t>(p.z)) * 0x165667B19E3779F9ULL;
        return h;
    }
};

inline Coord neighbor(Coord c, Face face) {
    switch (face) {
        case Face::NegX: --c.x; break;
        case Face::PosX: ++c.x; break;
        case Face::NegY: --c.y; break;
        case Face::PosY: ++c.y; break;
        case Face::NegZ: --c.z; break;
        case Face::PosZ: ++c.z; break;
    }
    return c;
}

// A transparent-space flood fill: never marks unknown/transparent blocks opaque.
// That conservative classification is essential to avoid disappearing caves.
template <typename Visitor>
std::uint8_t flood(const OpaqueBlocks &opaque, int start, std::array<bool, VOLUME> &visited,
                   Visitor visit) {
    if (opaque[start] || visited[start]) return 0;
    std::array<int, VOLUME> queue{};
    int head = 0, tail = 0;
    visited[start] = true;
    queue[tail++] = start;
    std::uint8_t faces = 0;
    while (head < tail) {
        const int at = queue[head++];
        const int x = at % SIDE;
        const int z = (at / SIDE) % SIDE;
        const int y = at / (SIDE * SIDE);
        visit(at);
        auto walk = [&](int xx, int yy, int zz, Face border) {
            if (xx < 0 || xx >= SIDE || yy < 0 || yy >= SIDE || zz < 0 || zz >= SIDE) {
                faces |= faceBit(border);
                return;
            }
            const int next = index(xx, yy, zz);
            if (!opaque[next] && !visited[next]) {
                visited[next] = true;
                queue[tail++] = next;
            }
        };
        walk(x - 1, y, z, Face::NegX);
        walk(x + 1, y, z, Face::PosX);
        walk(x, y - 1, z, Face::NegY);
        walk(x, y + 1, z, Face::PosY);
        walk(x, y, z - 1, Face::NegZ);
        walk(x, y, z + 1, Face::PosZ);
    }
    return faces;
}

struct SectionVisibility {
    std::array<std::uint8_t, 6> exits{};

    bool connects(Face incoming, Face outgoing) const {
        return (exits[faceIndex(incoming)] & faceBit(outgoing)) != 0;
    }

    // For a camera inside a solid/unknown cell, fail open instead of hiding geometry.
    static std::uint8_t cameraExits(const OpaqueBlocks &opaque, int x, int y, int z) {
        if (x < 0 || x >= SIDE || y < 0 || y >= SIDE || z < 0 || z >= SIDE ||
            opaque[index(x, y, z)]) {
            return 0x3Fu;
        }
        std::array<bool, VOLUME> visited{};
        return flood(opaque, index(x, y, z), visited, [](int) {});
    }

    static SectionVisibility fromOpaque(const OpaqueBlocks &opaque) {
        SectionVisibility result{};
        std::array<bool, VOLUME> visited{};
        for (int start = 0; start < VOLUME; ++start) {
            if (opaque[start] || visited[start]) continue;
            const auto borders = flood(opaque, start, visited, [](int) {});
            for (int f = 0; f < 6; ++f) {
                if (borders & (1u << f)) result.exits[f] |= borders;
            }
        }
        return result;
    }
};

// Output: which *loaded* sections are possibly visible from the camera.
// It is NOT safe to submit these as a game hook without version-specific
// renderer integration, transparent-block classification, and test coverage.
class VisibilityGraph {
public:
    void update(Coord coord, const OpaqueBlocks &opaque) {
        sections_[coord] = SectionVisibility::fromOpaque(opaque);
    }
    void remove(Coord coord) { sections_.erase(coord); }
    std::size_t size() const { return sections_.size(); }

    std::unordered_set<Coord, CoordHash> visible(Coord camera, std::uint8_t cameraExitMask) const {
        std::unordered_set<Coord, CoordHash> result;
        if (sections_.find(camera) == sections_.end()) return result;
        // Each section needs separate entrance-face visits. A section may
        // contain disconnected caves and cannot be visited only once.
        struct State { Coord pos; int entry; };
        std::queue<State> todo;
        std::unordered_map<Coord, std::uint8_t, CoordHash> entered;
        todo.push({camera, 6});
        while (!todo.empty()) {
            State state = todo.front();
            todo.pop();
            const auto it = sections_.find(state.pos);
            if (it == sections_.end()) continue;
            const std::uint8_t entryBit = static_cast<std::uint8_t>(1u << state.entry);
            if ((entered[state.pos] & entryBit) != 0) continue;
            entered[state.pos] |= entryBit;
            result.insert(state.pos);
            const auto out = (state.entry == 6)
                ? cameraExitMask
                : it->second.exits[state.entry];
            for (int f = 0; f < 6; ++f) {
                if ((out & (1u << f)) == 0) continue;
                const auto dir = static_cast<Face>(f);
                Coord target = neighbor(state.pos, dir);
                if (sections_.find(target) == sections_.end()) continue;
                todo.push({target, faceIndex(opposite(dir))});
            }
        }
        return result;
    }

private:
    std::unordered_map<Coord, SectionVisibility, CoordHash> sections_;
};

} // namespace fastclient::culling
