describe 'geometry', ->
  g = null
  beforeEach ->
    g = new Geometry()
    g.load([
      [[2,0,0],[2,2,0],[0,2,0],[0,0,0]],
      [[0,0,0],[0,0,2],[2,0,2],[2,0,0]],
      [[0,2,0],[0,2,2],[0,0,2],[0,0,0]],
      [[0,0,2],[0,2,2],[2,2,2],[2,0,2]],
      [[2,2,0],[2,2,2],[0,2,2],[0,2,0]],
      [[2,0,0],[2,0,2],[2,2,2],[2,2,0]]
    ])
    g.load([
      [[3,1,1],[3,3,1],[1,3,1],[1,1,1]],
      [[1,1,1],[1,1,3],[3,1,3],[3,1,1]],
      [[1,3,1],[1,3,3],[1,1,3],[1,1,1]],
      [[1,1,3],[1,3,3],[3,3,3],[3,1,3]],
      [[3,3,1],[3,3,3],[1,3,3],[1,3,1]],
      [[3,1,1],[3,1,3],[3,3,3],[3,3,1]]
    ])
    
   
  it 'should load a polyhedron and triangulate', ->
    expect(g.polyhedral.length).toEqual(2)
    
    for p, i in g.polyhedral
      expect(p.vertices.all().length).toEqual(8)
      expect(p.faces.length).toEqual(6)
      expect(p.edges.all().length).toEqual(12)
      
      expect(p.extent.min).toBeSameArrayAs [0 + i, 0 + i, 0 + i]
      expect(p.extent.max).toBeSameArrayAs [2 + i, 2 + i, 2 + i]
    
  
  it 'polygon normals should point outwards', ->
    polygons_touched = 0
    for polygon in g.polyhedral[0].faces
      for [coord, pos] in [['x', 0], ['x', 2], ['y', 0], ['y', 2], ['z', 0], ['z', 2]]
        if Utils.arrayAll(polygon.vertices, (a) -> a[coord] == pos)
          
          ref = for coord1 in ['x','y','z']
            if coord1 == coord
              if pos == 2 then 1 else -1
            else
              0
          ref.push pos
          
          expect(polygon.plane).toBeSameArrayAs ref
          expect(polygon.touched).toBeUndefined()
          
          polygon.touched = true
          polygons_touched += 1
    
    expect(polygons_touched).toEqual(6)
  
  it 'should return an intersection line segment', ->
    expect(g.findFaceIntersections(g.polyhedral[0].faces[4], g.polyhedral[1].faces[0]))
      .toBeDefined()
    expect(g.findFaceIntersections(g.polyhedral[0].faces[4], g.polyhedral[1].faces[4]))
      .toBeDefined()
  
  
  polygonize = (vertices) ->
    
    polygon = vertices.map (a) -> new Vertex3(a...)
    
    for i in [0...polygon.length]
      polygon[i].adjacents.add polygon[if i > 0 then i - 1 else polygon.length - 1]
      polygon[i].adjacents.add polygon[(i + 1) % polygon.length]
    polygon
  
  it 'should triangulate a monotone polygon', ->
    
    # setup
    polygon = polygonize [
      [0,0,0]
      [0,7,0]
      [3,8,0]
      [6,3,0]
      [7,6,0]
      [9,3,0]
      [7,0,0]
    ]
    
    # do the work
    polygon = Geometry.triangulateMonotone(Geometry.translateToXY polygon)
    
    for tri in polygon
      console.log tri[0].toString(), tri[1].toString(), tri[2].toString()
    
    # simple test to start
    expect(polygon.length).toEqual(5)
    
    # find all edges
    edges = []
    for tri in polygon
      edges.push [tri[0],tri[1]], [tri[1],tri[2]], [tri[2],tri[0]]
    
    # check whether any edge intersect another
    # O(n�) but I don't care
    for e1, i1 in edges
      p1 = e1[0]
      vec1 = e1[1].sub(p1)
      
      for e2, i2 in edges
        unless i1 == i2
          p2 = e2[0]
          vec2 = e2[1].sub(p2)
          
          # quick check whether the extents overlap
          if Geometry.overlaps2d(Geometry.calcExtent(e1), Geometry.calcExtent(e2))
            if (e1[0].equals(e2[0]) and e1[1].equals(e2[1])) or (e1[0].equals(e2[1]) and e1[1].equals(e2[0]))
              e1.colinear += 1
            else
              # thanks Gareth Rees
              # http://stackoverflow.com/questions/563198#565282
              qp = [p2.x - p1.x, p2.y - p1.y, p2.z - p1.z]
              qp1 = Math.crossProduct(qp, vec1)
              qp2 = Math.crossProduct(qp, vec2)
              rs = Math.crossProduct(vec1, vec2)
              
              t1 = Math.vecLength(qp1) / Math.vecLength(rs)
              t2 = Math.vecLength(qp2) / Math.vecLength(rs)
              
              # since Math.vecLength always return a positive number
              # we need to check both possible intersection points
              p11 = [p1.x + (-t1) * vec1[0], p1.y + (-t1) * vec1[1], p1.z + (-t1) * vec1[2]]
              p12 = [p1.x + t1 * vec1[0],    p1.y + t1 * vec1[1],    p1.z + t1 * vec1[2]]
              p21 = [p2.x + (-t2) * vec2[0], p2.y + (-t2) * vec2[1], p2.z + (-t2) * vec1[2]]
              p22 = [p2.x + t2 * vec2[0],    p2.y + t2 * vec2[1],    p2.z + t2 * vec1[2]]
              
              if Utils.arrayEquals(p11, p21)
                t1 = -t1
                t2 = -t2
              else if Utils.arrayEquals(p12, p21)
                t2 = -t2
              else if Utils.arrayEquals(p11, p22)
                t1 = -t1
              else unless Utils.arrayEquals(p12, p22)
                # oops, no intersection found
                continue
              
              # for intersection points 0 < t1, t2 < 1 would be valid
              # but we don't want any
              expect(t1).not.toBeStrictlyBetween(0, 1)
              expect(t2).not.toBeStrictlyBetween(0, 1)
    
    # each edge should only have one partner egde with same vertices
    for e in edges
      expect(e.colinear).toBeLessThan(2) if e.colinear
    
  
  it 'should split a polygon in monotones', ->
    # setup
    # plane: normal = [0, 0, 1], d = 0
    polygon = polygonize [
      [0,0,0],
      [0,10,0],
      [4,10,0],
      [2,9,0],
      [2,7,0],
      [7,8,0],
      [4,6,0],
      [5,3,0],
      [3,4,0],
      [1,1,0],
      [4,1,0],
      [6,2,0],
      [5,0,0]
    ]
    
    # do the work
    monotones = Geometry.monotonize(Geometry.translateToXY polygon)
    
    # simple test to start
    expect(monotones.length).toEqual(4)
    
    # make sure the monotone propery is ensured for each polygon
    for polygon in monotones
      polygon.sort (a, b) -> a.dy - b.dy or b.dx - a.dx
      
      first = polygon[0]
      last = polygon[polygon.length - 1]
      
      comp = (a, b) -> a.dy - b.dy || b.dx - a.dx
      
      unless polygon.length == 3
        i = 0
        v = first.adj0
        while v.adj0 != last
          expect(comp(v, v.adj0)).toBeLessThan 0
          v = v.adj0
          if i++ == 10000
            expect(i).toBeLessThan 10000
            break
        
        v = first.adj1
        while v.adj1 != last
          expect(comp(v, v.adj1)).toBeGreaterThan 0
          v = v.adj1
          if i++ == 20000
            expect(i).toBeLessThan 20000
            break

  
  it 'should project any plane into xy-plane', ->
    
    # setup
    # normal = [-3, 0, 1]
    vertices = [
      new Vertex3 3,4,5
      new Vertex3 4,6,8
      new Vertex3 3,5,5
    ]
    
    # do the work
    vertices2 = Geometry.translateToXY(vertices)
    
    # the x-coordinate should be gone
    for v, i in vertices2
      expect(vertices[i].y).toEqual(v.dx)
      expect(vertices[i].z).toEqual(v.dy)
    
    