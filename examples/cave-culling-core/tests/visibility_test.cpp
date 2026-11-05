#include "VisibilityCore.hpp"
#include <cassert>
#include <iostream>

using namespace fastclient::culling;

void clearXTunnel(OpaqueBlocks &opaque) {
    for (int x = 0; x < SIDE; ++x) opaque[index(x, 8, 8)] = 0;
}

int main() {
    OpaqueBlocks allAir{};
    OpaqueBlocks allStone{};
    allStone.fill(1);
    const auto open = SectionVisibility::fromOpaque(allAir);
    const auto solid = SectionVisibility::fromOpaque(allStone);
    assert(open.connects(Face::NegX, Face::PosX));
    assert(open.connects(Face::NegZ, Face::PosY));
    assert(!solid.connects(Face::NegX, Face::PosX));

    OpaqueBlocks separated{};
    for (int y = 0; y < SIDE; ++y)
        for (int z = 0; z < SIDE; ++z)
            separated[index(8, y, z)] = 1;
    const auto split = SectionVisibility::fromOpaque(separated);
    assert(!split.connects(Face::NegX, Face::PosX));

    OpaqueBlocks tunnel = allStone;
    clearXTunnel(tunnel);
    const auto tunnelVis = SectionVisibility::fromOpaque(tunnel);
    assert(tunnelVis.connects(Face::NegX, Face::PosX));
    assert(!tunnelVis.connects(Face::NegX, Face::PosY));
    const auto fromCamera = SectionVisibility::cameraExits(tunnel, 0, 8, 8);
    assert((fromCamera & faceBit(Face::PosX)) != 0);
    assert((fromCamera & faceBit(Face::PosZ)) == 0);
    assert(SectionVisibility::cameraExits(allStone, 8, 8, 8) == 0x3F);

    VisibilityGraph graph;
    const Coord a{0, 0, 0}, b{1, 0, 0}, c{2, 0, 0};
    graph.update(a, allAir);
    graph.update(b, allStone);
    graph.update(c, allAir);
    auto visible = graph.visible(a, SectionVisibility::cameraExits(allAir, 8, 8, 8));
    assert(visible.count(a) == 1 && visible.count(b) == 1 && visible.count(c) == 0);
    graph.update(b, tunnel); // dynamic block update invalidates cached reachability
    visible = graph.visible(a, SectionVisibility::cameraExits(allAir, 8, 8, 8));
    assert(visible.count(c) == 1);
    graph.remove(b);
    visible = graph.visible(a, SectionVisibility::cameraExits(allAir, 8, 8, 8));
    assert(visible.count(c) == 0);

    std::cout << "Fast Client cave visibility core: all tests passed\n";
    return 0;
}
